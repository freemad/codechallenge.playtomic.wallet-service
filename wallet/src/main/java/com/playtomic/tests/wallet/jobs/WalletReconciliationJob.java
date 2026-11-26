package com.playtomic.tests.wallet.jobs;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.configs.WalletReconciliationProps;
import com.playtomic.tests.wallet.constants.WalletWorkerParam;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.services.WalletOperationService;
import com.playtomic.tests.wallet.services.entity.WalletJobLeaseEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOperationEntityService;

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

    /** Connects lease management and operation recovery to the configured reconciliation schedule. */
    public WalletReconciliationJob(WalletOperationService operations, WalletOperationEntityService operationEntities,
                                   WalletJobLeaseEntityService leases, WalletReconciliationProps properties) {
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
        return operationEntities.reconciliationBatch(WALLET_OPERATION_STATUS_PENDING,
                        now.minus(properties.getStalePendingAge()), properties.getBatchSize())
                .concatMap(operation -> operations.markPaymentOutcome(operation.getId(), WALLET_OPERATION_STATUS_REVIEW_REQUIRED)
                        .flatMap(WalletOutcome::optionalValue)
                        .doOnNext(result -> {
                            if (result.getStatus() == WALLET_OPERATION_STATUS_REVIEW_REQUIRED) {
                                REVIEW.operation(result.getId()).log();
                            }
                        }))
                .thenMany(Flux.defer(() -> operationEntities.reconciliationBatch(
                        WALLET_OPERATION_STATUS_RECEIPT_RECORDED, now.minus(properties.getReceiptDelay()), properties.getBatchSize())))
                .concatMap(operation -> Mono.defer(() -> operations.apply(operation.getId()))
                        .flatMap(WalletOutcome::optionalValue).then()
                        .onErrorMap(ExceptionNormalizer::normalize)
                        .onErrorResume(BusinessException.class, error -> {
                            RECOVERY_FAILED.operation(operation.getId()).addKeyValue("errorCode", error.getErrorCode().getCode()).log();
                            return operationEntities.deferReceiptRecovery(operation);
                        }))
                .then();
    }
}