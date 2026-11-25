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
import com.playtomic.tests.wallet.services.entity.WalletEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOperationEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOutboxEntityService;
import com.playtomic.tests.wallet.services.entity.WalletPaymentReceiptEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

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

    public Mono<Outcome<Optional<WalletOperationModel>, Error>> find(String id) {
        return WalletOutcome.optional(operationEntityService.findById(id));
    }

    /** An empty optional means an operation already owns this wallet/key pair. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> reserve(WalletOperationModel request) {
        return WalletOutcome.optional(operationEntityService.insertIfAbsent(request));
    }

    public Mono<Outcome<Optional<WalletOperationModel>, Error>> markPaymentOutcome(String id,
                                                                                   WalletOperationStatus outcome) {
        return WalletOutcome.optional(markPaymentStatus(id, outcome));
    }

    private Mono<WalletOperationModel> markPaymentStatus(String id, WalletOperationStatus outcome) {
        if (outcome != WALLET_OPERATION_STATUS_REVIEW_REQUIRED && outcome != WALLET_OPERATION_STATUS_PAYMENT_REJECTED
                && outcome != WALLET_OPERATION_STATUS_PROVIDER_UNAVAILABLE) {
            return Mono.error(() -> normalize(new IllegalArgumentException("Invalid payment outcome")));
        }
        return Mono.defer(() -> operationEntityService.transitionTopUp(id, outcome, WALLET_OPERATION_STATUS_PENDING))
                .switchIfEmpty(Mono.defer(() -> operationEntityService.findById(id)));
    }

    public Mono<Outcome<Optional<WalletOperationModel>, Error>> recordReceipt(String id, String providerPaymentId) {
        return WalletOutcome.optional(walletTransactions.execute(Mono.defer(() ->
                operationEntityService.transitionTopUp(id, WALLET_OPERATION_STATUS_RECEIPT_RECORDED,
                                WALLET_OPERATION_STATUS_PENDING, WALLET_OPERATION_STATUS_REVIEW_REQUIRED)
                        .flatMap(operation -> receiptEntityService.insert(new WalletPaymentReceiptModel(providerPaymentId, id,
                                Instant.now())).thenReturn(operation))
                        .switchIfEmpty(Mono.defer(() -> operationEntityService.findById(id)))))
                .onErrorResume(walletTransactions::isDuplicateKey,
                        ignored -> markPaymentStatus(id, WALLET_OPERATION_STATUS_REVIEW_REQUIRED)));
    }

    /** Only local work is retried. A recorded receipt stays recoverable if this transaction fails. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> apply(String id) {
        return WalletOutcome.optional(walletTransactions.execute(Mono.defer(() -> operationEntityService.findById(id).flatMap(operation -> {
            boolean debit = operation.getType() == WALLET_OPERATION_TYPE_SPEND
                    && operation.getStatus() == WALLET_OPERATION_STATUS_PENDING;
            boolean credit = operation.getType() == WALLET_OPERATION_TYPE_TOP_UP
                    && operation.getStatus() == WALLET_OPERATION_STATUS_RECEIPT_RECORDED;
            if (!debit && !credit) {
                return Mono.just(operation);
            }
            return entityService.findById(operation.getWalletId())
                    .filter(wallet -> wallet.getCurrency().equals(operation.getCurrency()))
                    .flatMap(wallet -> applyToWallet(operation, wallet, debit))
                    .switchIfEmpty(Mono.defer(() -> debit ? finish(operation, WALLET_OPERATION_STATUS_WALLET_UNAVAILABLE, null)
                            : Mono.error(() -> normalize(new IllegalStateException(
                                    "Recorded payment wallet or currency unavailable")))));
        }))));
    }

    private Mono<WalletOperationModel> applyToWallet(WalletOperationModel operation, WalletModel wallet,
                                                    boolean debit) {
        BigDecimal amount = operation.getAmount();
        if (debit && wallet.getBalance().compareTo(amount) < 0) {
            return finish(operation, WALLET_OPERATION_STATUS_INSUFFICIENT_FUNDS, null);
        }
        BigDecimal delta = debit ? amount.negate() : amount;
        return entityService.incrementBalance(wallet, delta, debit ? Optional.of(amount) : Optional.empty())
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
