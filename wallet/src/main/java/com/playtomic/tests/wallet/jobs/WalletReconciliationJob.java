package com.playtomic.tests.wallet.jobs;

import com.playtomic.tests.common.contracts.StripeChargeEvidence;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.configs.WalletReconciliationProps;
import com.playtomic.tests.wallet.constants.WalletWorkerParam;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.services.WalletOperationService;
import com.playtomic.tests.wallet.services.client.StripeClient;
import com.playtomic.tests.wallet.services.entity.WalletJobLeaseEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOperationEntityService;

import static com.playtomic.tests.wallet.configs.WalletPaymentProviderProps.STRIPE;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_PAYMENT_REJECTED;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_PENDING;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_RECEIPT_RECORDED;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_REVIEW_REQUIRED;
import static com.playtomic.tests.wallet.observability.WalletLogEvent.*;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Claims a lease before reconciling stale operations without dispatching another charge. */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = WalletReconciliationProps.PREFIX, name = "enabled", havingValue = "true")
public class WalletReconciliationJob {
    private static final String JOB = "payment-reconciliation";

    private final WalletOperationService operations;
    private final WalletOperationEntityService operationEntities;
    private final WalletJobLeaseEntityService leases;
    private final WalletReconciliationProps properties;
    private final Mono<String> indexReady;
    private final StripeClient stripe;

    /** Connects lease management and operation recovery to the configured reconciliation schedule. */
    public WalletReconciliationJob(WalletOperationService operations, WalletOperationEntityService operationEntities,
                                   WalletJobLeaseEntityService leases, WalletReconciliationProps properties, StripeClient stripe) {
        this.stripe = stripe;
        this.operations = operations;
        this.operationEntities = operationEntities;
        this.leases = leases;
        this.properties = properties;
        this.indexReady = WalletWorkerParam.cacheSuccessfulIndex(Mono.defer(operationEntities::ensureReconciliationIndex));
    }

    /** Claims the job lease and processes a bounded batch of stale operations. */
    @Scheduled(fixedDelayString = "${" + WalletReconciliationProps.PREFIX + ".interval}")
    public Mono<Void> reconcile() {
        return Mono.usingWhen(Mono.defer(() -> leases.acquire(JOB, properties.getLease())),
                token -> indexReady.then(Mono.defer(this::reconcileBatch)).timeout(properties.getRunTimeout()),
                token -> leases.release(JOB, token))
                .onErrorMap(ExceptionNormalizer::normalize)
                .doOnError(error -> RECONCILIATION_FAILED.fields().log());
    }

    private Mono<Void> reconcileBatch() {
        Instant now = Instant.now();
        var pending = Flux.defer(() -> operationEntities.reconciliationBatch(WALLET_OPERATION_STATUS_PENDING,
                        now.minus(properties.getStalePendingAge()), properties.getBatchSize()))
                .concatMap(operation -> operations.markPaymentOutcome(operation.getId(), WALLET_OPERATION_STATUS_REVIEW_REQUIRED)
                        .flatMap(WalletOutcome::optionalValue)
                        .doOnNext(result -> {
                            if (result.getStatus() == WALLET_OPERATION_STATUS_REVIEW_REQUIRED) {
                                REVIEW.operation(result.getId()).log();
                            }
                        }))
                .then().onErrorMap(ExceptionNormalizer::normalize)
                .onErrorResume(BusinessException.class, error -> {
                    if (ExceptionNormalizer.mustPropagate(error)) return Mono.error(error);
                    RECONCILIATION_FAILED.fields().addKeyValue("batch", "pending").log();
                    return Mono.empty();
                });
        var receipts = Flux.defer(() -> operationEntities.reconciliationBatch(
                        WALLET_OPERATION_STATUS_RECEIPT_RECORDED, now.minus(properties.getReceiptDelay()), properties.getBatchSize()))
                .concatMap(operation -> Mono.defer(() -> operations.apply(operation.getId()))
                        .flatMap(WalletOutcome::optionalValue).then()
                        .onErrorMap(ExceptionNormalizer::normalize)
                        .onErrorResume(BusinessException.class, error -> {
                            if (ExceptionNormalizer.mustPropagate(error)) return Mono.error(error);
                            RECOVERY_FAILED.operation(operation.getId()).addKeyValue("errorCode", error.getErrorCode().getCode()).log();
                            return operationEntities.deferReceiptRecovery(operation);
                        }))
                .then();
        var reviews = Flux.defer(() -> operationEntities.reconciliationBatch(WALLET_OPERATION_STATUS_REVIEW_REQUIRED,
                        now.minus(properties.getInterval()), properties.getBatchSize()))
                .concatMap(this::review).then();
        return Mono.when(receipts, pending, reviews);
    }

    private Mono<Void> review(WalletOperationModel operation) {
        return Mono.defer(() -> {
            if (!STRIPE.equals(operation.getProvider()) || operation.getProviderReference() == null) return Mono.<StripeChargeEvidence>empty();
            return stripe.lookup(UUID.fromString(operation.getProviderReference())).timeout(properties.getRunTimeout().dividedBy(2));
        }).filter(evidence -> evidence.reference().toString().equals(operation.getProviderReference())
                && evidence.currency().equals(operation.getCurrency()) && evidence.amount().compareTo(operation.getAmount()) == 0)
                .flatMap(evidence -> switch (evidence.status()) {
                    case DECLINED -> operationEntities.transitionTopUp(operation.getId(), WALLET_OPERATION_STATUS_PAYMENT_REJECTED, WALLET_OPERATION_STATUS_REVIEW_REQUIRED);
                    case ACCEPTED -> operations.recordReceipt(operation.getId(), evidence.receiptId()).flatMap(WalletOutcome::optionalValue);
                })
                .filter(updated -> updated.getStatus() != WALLET_OPERATION_STATUS_REVIEW_REQUIRED)
                .onErrorMap(ExceptionNormalizer::normalize)
                .onErrorResume(BusinessException.class, error -> {
                    if (ExceptionNormalizer.mustPropagate(error)) return Mono.error(error);
                    RECOVERY_FAILED.operation(operation.getId()).addKeyValue("errorCode", error.getErrorCode().getCode()).log();
                    return Mono.empty();
                }).switchIfEmpty(Mono.defer(() -> operationEntities.deferReview(operation).then(Mono.empty()))).then();
    }
}