package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.constants.WalletEventParam;
import com.playtomic.tests.wallet.contracts.IWalletTransactions;
import com.playtomic.tests.wallet.enums.WalletEventType;
import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.models.WalletPaymentReceiptModel;
import com.playtomic.tests.wallet.observability.WalletLogEvent;
import com.playtomic.tests.wallet.services.entity.WalletEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOperationEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOutboxEntityService;
import com.playtomic.tests.wallet.services.entity.WalletPaymentReceiptEntityService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.bson.types.Decimal128;
import com.playtomic.tests.wallet.enums.WalletErrorCode;

import static com.playtomic.tests.common.utils.ExceptionNormalizer.normalize;
import static com.playtomic.tests.wallet.enums.WalletErrorCode.BALANCE_UPDATE_NOT_MATCHED;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.*;
import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_SPEND;
import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP;

/**
 * Internal wallet operation commands shared by mutations and reconciliation.
 * Transaction failures stay on the reactive error channel for rollback and retry;
 * {@link WalletMutationService} translates them at the business boundary.
 */
@Service
@RequiredArgsConstructor
public class WalletOperationService {
    private final WalletOperationEntityService operationEntityService;
    private final WalletEntityService entityService;
    private final WalletPaymentReceiptEntityService receiptEntityService;
    private final WalletOutboxEntityService outboxEntityService;
    private final IWalletTransactions walletTransactions;

    @Value
    private static class Transition {
        WalletOperationModel operation;
        boolean changed;

        WalletOperationModel report(WalletLogEvent event) {
            if (changed) {
                event.operation(operation.getId()).addKeyValue("status", operation.getStatus().getName())
                        .addKeyValue("operationType", operation.getType().getName()).log();
            }
            return operation;
        }
    }

    /** Looks up the recorded operation, representing absence as an empty optional result. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> find(String id) {
        return WalletOutcome.optional(operationEntityService.findById(id));
    }

    /** An empty optional means an operation already owns this wallet/key pair. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> reserve(WalletOperationModel request) {
        var insert = operationEntityService.insertIfAbsent(request);
        if (request.getType() == WALLET_OPERATION_TYPE_TOP_UP) {
            insert = walletTransactions.execute(insert.flatMap(operation -> entityService
                    .fenceAdmission(request.getWalletId(), request.getCurrency())
                    .flatMap(wallet -> checkCapacity(wallet.getId(), wallet.getBalance()))
                    .thenReturn(operation)));
        }
        return WalletOutcome.optional(insert.onErrorResume(walletTransactions::isDuplicateKey, ignored -> Mono.empty()));
    }

    /** Records the provider outcome for an operation in its expected payment state. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> markPaymentOutcome(String id,
                                                                                   WalletOperationStatus outcome) {
        return WalletOutcome.optional(markPaymentStatus(id, outcome));
    }

    /** Records provider acceptance and its receipt transactionally before local crediting. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> recordReceipt(String id, String providerPaymentId) {
        return WalletOutcome.optional(walletTransactions.execute(Mono.defer(() ->
                operationEntityService.transitionTopUp(id, WALLET_OPERATION_STATUS_RECEIPT_RECORDED,
                                WALLET_OPERATION_STATUS_PENDING, WALLET_OPERATION_STATUS_REVIEW_REQUIRED)
                        .flatMap(operation -> receiptEntityService.insert(new WalletPaymentReceiptModel(providerPaymentId, id,
                                Instant.now())).thenReturn(new Transition(operation, true)))
                        .switchIfEmpty(Mono.defer(() -> operationEntityService.findById(id).map(existing -> new Transition(existing, false))))))
                .map(change -> change.report(WalletLogEvent.RECEIPT_COMMITTED))
                .onErrorResume(walletTransactions::isDuplicateKey, ignored -> {
                    WalletLogEvent.DUPLICATE_RECEIPT.operation(id).log();
                    return markPaymentStatus(id, WALLET_OPERATION_STATUS_REVIEW_REQUIRED);
                }));
    }

    /** Only local work is retried. A recorded receipt stays recoverable if this transaction fails. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> apply(String id) {
        Mono<Transition> work = Mono.defer(() -> operationEntityService.findById(id).flatMap(operation -> {
            boolean debit = operation.getType() == WALLET_OPERATION_TYPE_SPEND
                    && operation.getStatus() == WALLET_OPERATION_STATUS_PENDING;
            boolean credit = operation.getType() == WALLET_OPERATION_TYPE_TOP_UP
                    && operation.getStatus() == WALLET_OPERATION_STATUS_RECEIPT_RECORDED;
            if (!debit && !credit) {
                return Mono.just(new Transition(operation, false));
            }
            return entityService.findById(operation.getWalletId())
                    .filter(wallet -> wallet.getCurrency().equals(operation.getCurrency()))
                    .flatMap(wallet -> applyToWallet(operation, wallet, debit))
                    .switchIfEmpty(Mono.defer(() -> debit ? finish(operation, WALLET_OPERATION_STATUS_WALLET_UNAVAILABLE, null)
                            : Mono.error(() -> normalize(new IllegalStateException(
                                    "Recorded payment wallet or currency unavailable")))))
                    .map(updated -> new Transition(updated, true));
        }));
        return WalletOutcome.optional(walletTransactions.execute(work).map(change -> change.report(WalletLogEvent.FINALIZED)));
    }

    private Mono<WalletOperationModel> markPaymentStatus(String id, WalletOperationStatus outcome) {
        if (outcome != WALLET_OPERATION_STATUS_REVIEW_REQUIRED && outcome != WALLET_OPERATION_STATUS_PAYMENT_REJECTED
                && outcome != WALLET_OPERATION_STATUS_PROVIDER_UNAVAILABLE) {
            return Mono.error(() -> normalize(new IllegalArgumentException("Invalid payment outcome")));
        }
        return Mono.defer(() -> operationEntityService.transitionTopUp(id, outcome, WALLET_OPERATION_STATUS_PENDING))
                .switchIfEmpty(Mono.defer(() -> operationEntityService.findById(id)));
    }

    /** One hold needs only an exact endpoint. For multiple holds, bound every subset sum
     * at the finest participating decimal place, independently of receipt arrival order. */
    private Mono<Void> checkCapacity(String walletId, BigDecimal balance) {
        record Envelope(BigDecimal total, int count) { }
        return operationEntityService.unsettledCredits(walletId)
                .reduce(new Envelope(balance.stripTrailingZeros(), 0), (e, amount) -> {
                    var term = amount.stripTrailingZeros();
                    var total = e.count() == 0 && balance.signum() == 0 ? term : e.total().add(term);
                    return new Envelope(total, e.count() + 1);
                }).doOnNext(e -> {
                    if (e.count() == 0) return;
                    try {
                        new Decimal128(e.total());
                        if (e.count() > 1 && (e.total().precision() > 34 || balance.setScale(e.total().scale()).precision() > 34)) {
                            throw new NumberFormatException("Intermediate balance may round");
                        }
                    } catch (NumberFormatException ex) {
                        throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "Insufficient exact Decimal128 credit capacity", ex);
                    }
                }).then();
    }

    private Mono<WalletOperationModel> applyToWallet(WalletOperationModel operation, WalletModel wallet,
                                                    boolean debit) {
        BigDecimal amount = operation.getAmount();
        if (debit && wallet.getBalance().compareTo(amount) < 0) {
            return finish(operation, WALLET_OPERATION_STATUS_INSUFFICIENT_FUNDS, null);
        }
        BigDecimal delta = debit ? amount.negate() : amount;
        var capacity = debit ? checkCapacity(wallet.getId(), wallet.getBalance().add(delta)) : Mono.<Void>empty();
        return capacity.then(Mono.defer(() -> entityService.incrementBalance(wallet, delta, debit ? Optional.of(amount) : Optional.empty())))
                .switchIfEmpty(Mono.error(() -> new BusinessException(BALANCE_UPDATE_NOT_MATCHED)))
                .flatMap(updated -> finish(operation, WALLET_OPERATION_STATUS_SUCCEEDED, updated.getBalance())
                        .flatMap(result -> outboxEntityService.enqueue(new WalletOutboxModel(result.getId(), result.getWalletId(),
                                        debit ? WalletEventType.WALLET_DEBITED : WalletEventType.WALLET_CREDITED,
                                        WalletEventParam.SCHEMA_VERSION_V1, amount, result.getCurrency(),
                                        result.getResultingBalance(), result.getUpdatedAt(), result.getUpdatedAt(),
                                        null, null))
                                .thenReturn(result)));
    }

    private Mono<WalletOperationModel> finish(WalletOperationModel operation, WalletOperationStatus status,
                                              BigDecimal balance) {
        return operationEntityService.finalizeOperation(operation.getId(), operation.getStatus(), status, balance)
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Operation changed during finalization"))));
    }
}