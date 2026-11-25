package com.playtomic.tests.wallet.jobs;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.configs.WalletReconciliationProps;
import com.playtomic.tests.wallet.constants.WalletWorkerParam;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.services.WalletOperationService;
import com.playtomic.tests.wallet.services.entity.WalletJobLeaseEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOperationEntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_PENDING;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_RECEIPT_RECORDED;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_REVIEW_REQUIRED;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = WalletReconciliationProps.PREFIX, name = "enabled", havingValue = "true")
public class WalletReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(WalletReconciliationJob.class);
    private static final String JOB = "payment-reconciliation";

    private final WalletOperationService operations;
    private final WalletOperationEntityService operationEntities;
    private final WalletJobLeaseEntityService leases;
    private final WalletReconciliationProps properties;
    private final Mono<String> indexReady;

    public WalletReconciliationJob(WalletOperationService operations, WalletOperationEntityService operationEntities,
                                   WalletJobLeaseEntityService leases, WalletReconciliationProps properties) {
        this.operations = operations;
        this.operationEntities = operationEntities;
        this.leases = leases;
        this.properties = properties;
        this.indexReady = WalletWorkerParam.cacheSuccessfulIndex(Mono.defer(operationEntities::ensureReconciliationIndex));
    }

    @Scheduled(fixedDelayString = "${" + WalletReconciliationProps.PREFIX + ".interval}")
    public Mono<Void> reconcile() {
        return Mono.usingWhen(Mono.defer(() -> leases.acquire(JOB, properties.lease())),
                token -> indexReady.then(Mono.defer(this::reconcileBatch)).timeout(properties.runTimeout()),
                token -> leases.release(JOB, token))
                .onErrorMap(ExceptionNormalizer::normalize);
    }

    private Mono<Void> reconcileBatch() {
        Instant now = Instant.now();
        return operationEntities.reconciliationBatch(WALLET_OPERATION_STATUS_PENDING, now.minus(properties.stalePendingAge()), properties.batchSize())
                .concatMap(operation -> operations.markPaymentOutcome(operation.getId(), WALLET_OPERATION_STATUS_REVIEW_REQUIRED)
                        .flatMap(WalletOutcome::optionalValue)
                        .doOnNext(result -> {
                            if (result.getStatus() == WALLET_OPERATION_STATUS_REVIEW_REQUIRED) {
                                log.warn("Stale payment requires review: operationId={}", result.getId());
                            }
                        }))
                .thenMany(Flux.defer(() -> operationEntities.reconciliationBatch(
                        WALLET_OPERATION_STATUS_RECEIPT_RECORDED, now.minus(properties.receiptDelay()), properties.batchSize())))
                .concatMap(operation -> Mono.defer(() -> operations.apply(operation.getId()))
                        .flatMap(WalletOutcome::optionalValue).then()
                        .onErrorMap(ExceptionNormalizer::normalize)
                        .onErrorResume(BusinessException.class, error -> {
                            log.warn("Recorded payment could not be applied: operationId={} errorCode={}",
                                    operation.getId(), error.getErrorCode().getCode());
                            return operationEntities.deferReceiptRecovery(operation);
                        }))
                .then();
    }
}