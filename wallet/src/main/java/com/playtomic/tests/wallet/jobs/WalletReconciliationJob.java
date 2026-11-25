package com.playtomic.tests.wallet.jobs;

import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.services.WalletJobLeaseService;
import com.playtomic.tests.wallet.services.WalletOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

import static com.playtomic.tests.wallet.models.WalletOperationModel.Status.PENDING;
import static com.playtomic.tests.wallet.models.WalletOperationModel.Status.RECEIPT_RECORDED;
import static com.playtomic.tests.wallet.models.WalletOperationModel.Status.REVIEW_REQUIRED;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "wallet.reconciliation.enabled", havingValue = "true")
public class WalletReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(WalletReconciliationJob.class);
    private static final String JOB = "payment-reconciliation";
    private static final Duration LEASE = Duration.ofMinutes(1);
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration STALE_PENDING = Duration.ofMinutes(2);
    private static final Duration RECEIPT_DELAY = Duration.ofSeconds(15);
    private static final int BATCH_SIZE = 25;

    private final WalletOperationService operations;
    private final WalletJobLeaseService leases;
    private final Mono<String> indexReady;

    public WalletReconciliationJob(WalletOperationService operations, WalletJobLeaseService leases) {
        this.operations = operations;
        this.leases = leases;
        this.indexReady = Mono.defer(operations::ensureReconciliationIndex)
                .flatMap(WalletOutcome::value)
                .cache(value -> Duration.ofMillis(Long.MAX_VALUE), error -> Duration.ZERO, () -> Duration.ZERO);
    }

    @Scheduled(fixedDelayString = "${wallet.reconciliation.interval:15s}")
    public Mono<Void> reconcile() {
        return Mono.usingWhen(leases.acquire(JOB, LEASE).flatMap(WalletOutcome::optionalValue),
                token -> indexReady.then(Mono.defer(this::reconcileBatch)).timeout(RUN_TIMEOUT),
                token -> leases.release(JOB, token).flatMap(WalletOutcome::value));
    }

    private Mono<Void> reconcileBatch() {
        Instant now = Instant.now();
        return operations.reconciliationBatch(PENDING, now.minus(STALE_PENDING), BATCH_SIZE)
                .concatMap(WalletOutcome::value)
                .concatMap(operation -> operations.markPaymentOutcome(operation.getId(), REVIEW_REQUIRED)
                        .flatMap(WalletOutcome::optionalValue)
                        .doOnNext(result -> {
                            if (result.getStatus() == REVIEW_REQUIRED) {
                                log.warn("Stale payment requires review: operationId={}", result.getId());
                            }
                        }))
                .thenMany(operations.reconciliationBatch(RECEIPT_RECORDED, now.minus(RECEIPT_DELAY), BATCH_SIZE))
                .concatMap(WalletOutcome::value)
                .concatMap(operation -> operations.apply(operation.getId())
                        .flatMap(WalletOutcome::optionalValue).then().onErrorResume(error -> {
                            log.warn("Recorded payment could not be applied: operationId={} errorType={}",
                                    operation.getId(), error.getClass().getSimpleName());
                            return operations.deferReceiptRecovery(operation).flatMap(WalletOutcome::value).then();
                        }))
                .then();
    }
}