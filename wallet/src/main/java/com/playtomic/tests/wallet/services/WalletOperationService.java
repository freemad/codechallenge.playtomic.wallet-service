package com.playtomic.tests.wallet.services;

import com.mongodb.MongoException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.contracts.IWalletEntityAccess;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.mappers.WalletOperationEntityMapper;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.models.WalletOutcome.Completion;
import com.playtomic.tests.wallet.models.WalletPaymentReceiptModel;
import com.playtomic.tests.wallet.repositories.WalletOperationRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bson.types.Decimal128;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.playtomic.tests.wallet.models.WalletOperationModel.Status.*;
import static com.playtomic.tests.wallet.models.WalletOperationModel.Type.SPEND;
import static com.playtomic.tests.wallet.models.WalletOperationModel.Type.TOP_UP;

/**
 * Internal wallet operation commands shared by mutations and reconciliation.
 * Transaction failures stay on the reactive error channel for rollback and retry;
 * {@link WalletMutationService} translates them at the business boundary.
 */
@Service
@RequiredArgsConstructor
public class WalletOperationService implements IWalletEntityAccess<WalletOperationModel, WalletOperationEntity> {
    @Getter(onMethod_ = @Override)
    private final WalletOperationRepository repository;
    @Getter(onMethod_ = @Override)
    private final WalletOperationEntityMapper mapper;
    private final WalletService wallets;
    private final WalletPaymentReceiptService receipts;
    private final WalletOutboxService outbox;
    private final TransactionalOperator walletTransactions;

    public Mono<Outcome<Optional<WalletOperationModel>, Error>> find(String id) {
        return WalletOutcome.optional(findOperation(id));
    }

    private Mono<WalletOperationModel> findOperation(String id) {
        return repository.findById(id).flatMap(this::mapOperation);
    }

    private Mono<WalletOperationModel> mapOperation(WalletOperationEntity entity) {
        return WalletOutcome.required(mapper.toMonoModel(entity), "Wallet operation could not be mapped");
    }

    public Mono<Outcome<String, Error>> ensureReconciliationIndex() {
        return WalletOutcome.required(repository.ensureReconciliationIndex(), "Reconciliation index was not created")
                .map(Outcome::success);
    }

    public Flux<Outcome<WalletOperationModel, Error>> reconciliationBatch(WalletOperationModel.Status status,
                                                                          Instant cutoff, int limit) {
        return repository.reconciliationBatch(status, cutoff, limit).map(mapper::toModel).map(Outcome::success);
    }

    public Mono<Outcome<Completion, Error>> deferReceiptRecovery(WalletOperationModel operation) {
        return WalletOutcome.completed(repository.deferReceiptRecovery(operation.getId(), operation.getUpdatedAt()));
    }

    /** An empty optional means an operation already owns this wallet/key pair. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> reserve(WalletOperationModel request) {
        return WalletOutcome.optional(Mono.defer(() -> {
            new Decimal128(request.getAmount()); // Reject values that the storage format would round.
            return WalletOutcome.required(mapper.toMonoEntity(request).flatMap(repository::insert),
                    "Wallet operation was not inserted");
        }).onErrorResume(DuplicateKeyException.class, ignored -> Mono.empty()).flatMap(this::mapOperation));
    }

    public Mono<Outcome<Optional<WalletOperationModel>, Error>> markPaymentOutcome(String id,
                                                                                     WalletOperationModel.Status outcome) {
        return WalletOutcome.optional(markPaymentStatus(id, outcome));
    }

    private Mono<WalletOperationModel> markPaymentStatus(String id, WalletOperationModel.Status outcome) {
        if (outcome != REVIEW_REQUIRED && outcome != PAYMENT_REJECTED && outcome != PROVIDER_UNAVAILABLE) {
            return Mono.error(new IllegalArgumentException("Invalid payment outcome"));
        }
        return Mono.defer(() -> repository.transitionTopUp(id, outcome, PENDING))
                .flatMap(this::mapOperation).switchIfEmpty(Mono.defer(() -> findOperation(id)));
    }

    public Mono<Outcome<Optional<WalletOperationModel>, Error>> recordReceipt(String id, String providerPaymentId) {
        return WalletOutcome.optional(inTransaction(Mono.defer(() ->
                repository.transitionTopUp(id, RECEIPT_RECORDED, PENDING, REVIEW_REQUIRED)
                        .flatMap(operation -> receipts.insert(new WalletPaymentReceiptModel(providerPaymentId, id,
                                Instant.now())).flatMap(WalletOutcome::value).thenReturn(operation))
                        .switchIfEmpty(Mono.defer(() -> repository.findById(id)))))
                .flatMap(this::mapOperation)
                .onErrorResume(DuplicateKeyException.class, ignored -> markPaymentStatus(id, REVIEW_REQUIRED)));
    }

    /** Only local work is retried. A recorded receipt stays recoverable if this transaction fails. */
    public Mono<Outcome<Optional<WalletOperationModel>, Error>> apply(String id) {
        return WalletOutcome.optional(inTransaction(Mono.defer(() -> repository.findById(id).flatMap(operation -> {
            boolean debit = operation.getType() == SPEND && operation.getStatus() == PENDING;
            boolean credit = operation.getType() == TOP_UP && operation.getStatus() == RECEIPT_RECORDED;
            if (!debit && !credit) {
                return Mono.just(operation);
            }
            return wallets.findById(operation.getWalletId())
                    .filter(wallet -> wallet.getCurrency().equals(operation.getCurrency()))
                    .flatMap(wallet -> applyToWallet(operation, wallet, debit))
                    .switchIfEmpty(Mono.defer(() -> debit ? finish(operation, WALLET_UNAVAILABLE, null)
                            : Mono.error(new IllegalStateException("Recorded payment wallet or currency unavailable"))));
        }))).flatMap(this::mapOperation));
    }

    private Mono<WalletOperationEntity> applyToWallet(WalletOperationEntity operation, WalletModel wallet,
                                                     boolean debit) {
        BigDecimal amount = operation.getAmount();
        if (debit && wallet.getBalance().compareTo(amount) < 0) {
            return finish(operation, INSUFFICIENT_FUNDS, null);
        }
        BigDecimal delta = debit ? amount.negate() : amount;
        new Decimal128(wallet.getBalance().add(delta)); // Validate exactness in the transaction snapshot.
        return wallets.incrementBalance(wallet.getId(), operation.getCurrency(), delta,
                        debit ? Optional.of(amount) : Optional.empty())
                .flatMap(outcome -> {
                    if (outcome.isFailure()) {
                        return Mono.error(new IllegalStateException(outcome.getError().getMessage()));
                    }
                    return finish(operation, SUCCEEDED, outcome.getData().getBalance())
                            .flatMap(result -> outbox.enqueue(new WalletOutboxModel(result.getId(), result.getWalletId(),
                                            debit ? "WalletDebited" : "WalletCredited", 1, amount, result.getCurrency(),
                                            result.getResultingBalance(), result.getUpdatedAt(), result.getUpdatedAt(),
                                            null, null))
                                    .flatMap(WalletOutcome::value)
                                    .thenReturn(result));
                });
    }

    private Mono<WalletOperationEntity> finish(WalletOperationEntity operation, WalletOperationModel.Status status,
                                               BigDecimal balance) {
        return repository.finalizeOperation(operation.getId(), operation.getStatus(), status, balance)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("Operation changed during finalization")));
    }

    private <T> Mono<T> inTransaction(Mono<T> work) {
        return walletTransactions.execute(transaction -> work).singleOrEmpty()
                .retryWhen(Retry.backoff(5, Duration.ofMillis(10)).maxBackoff(Duration.ofMillis(250))
                        .filter(WalletOperationService::isTransientTransactionError)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private static boolean isTransientTransactionError(Throwable error) {
        boolean transientError = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongoError) {
                if (mongoError.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    return false;
                }
                transientError |= mongoError.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
            }
        }
        return transientError;
    }
}
