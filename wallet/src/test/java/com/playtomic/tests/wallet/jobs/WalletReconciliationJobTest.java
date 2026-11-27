package com.playtomic.tests.wallet.jobs;

import com.playtomic.tests.common.contracts.StripeChargeEvidence;
import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.enums.StripeChargeStatus;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.configs.WalletReconciliationProps;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.services.WalletOperationService;
import com.playtomic.tests.wallet.services.client.StripeClient;
import com.playtomic.tests.wallet.services.entity.WalletJobLeaseEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOperationEntityService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionException;

import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_PENDING;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_RECEIPT_RECORDED;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_REVIEW_REQUIRED;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_PAYMENT_REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WalletReconciliationJobTest {
    private static final String JOB = "payment-reconciliation";
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final WalletReconciliationProps SETTINGS = new WalletReconciliationProps(
            Duration.ofSeconds(10), Duration.ofSeconds(40), Duration.ofSeconds(3),
            Duration.ofMinutes(3), Duration.ofSeconds(7), 4);

    private final WalletOperationService paymentCommands = mock(WalletOperationService.class);
    private final WalletOperationEntityService operations = mock(WalletOperationEntityService.class);
    private final WalletJobLeaseEntityService leases = mock(WalletJobLeaseEntityService.class);
    private final StripeClient stripe = mock(StripeClient.class);
    private final WalletReconciliationJob job = new WalletReconciliationJob(paymentCommands, operations, leases, SETTINGS, stripe);

    @org.junit.jupiter.api.BeforeEach
    void emptyReviewBatch() {
        when(operations.reconciliationBatch(eq(WALLET_OPERATION_STATUS_REVIEW_REQUIRED), any(), anyInt())).thenReturn(Flux.empty());
    }

    @Test
    void skipsWorkWhenTheLeaseIsHeldElsewhere() {
        when(leases.acquire(eq(JOB), any(Duration.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(job.reconcile()).expectComplete().verify(DEADLINE);

        verify(leases).acquire(eq(JOB), any(Duration.class));
        verifyNoInteractions(paymentCommands, operations);
        verifyNoMoreInteractions(leases);
    }

    @Test
    void releasesTheAcquiredTokenWhenReconciliationIsCancelled() {
        String token = "lease-token";
        var cleanup = PublisherProbe.<Void>empty();
        when(leases.acquire(eq(JOB), any(Duration.class))).thenReturn(Mono.just(token));
        when(operations.ensureReconciliationIndex()).thenReturn(Mono.just("index"));
        when(operations.reconciliationBatch(any(), any(), anyInt())).thenReturn(Flux.never());
        when(leases.release(JOB, token)).thenReturn(cleanup.mono());

        StepVerifier.create(job.reconcile())
                .then(() -> verify(operations).reconciliationBatch(eq(WALLET_OPERATION_STATUS_PENDING), any(), anyInt()))
                .thenCancel().verify(DEADLINE);

        verify(leases).release(JOB, token);
        cleanup.assertWasSubscribed();
    }

    @Test
    void normalizesAnIndexFailureAfterReleasingTheLease() {
        var failure = new IllegalStateException("index unavailable");
        var cleanup = PublisherProbe.<Void>empty();
        when(leases.acquire(JOB, SETTINGS.getLease())).thenReturn(Mono.just("lease"));
        when(leases.release(JOB, "lease")).thenReturn(cleanup.mono());
        when(operations.ensureReconciliationIndex()).thenReturn(Mono.error(failure));

        StepVerifier.create(job.reconcile())
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(BusinessException.class, business -> {
                    assertThat(business.getErrorCode()).isEqualTo(CommonErrorCode.ILLEGAL_STATE_EXCEPTION);
                    assertThat(business.getCause()).isSameAs(failure);
                })).verify(DEADLINE);
        cleanup.assertWasSubscribed();
    }

    @Test
    void defersAnOrdinaryReceiptFailureButNotAWrappedInterruption() {
        var operation = mock(WalletOperationModel.class);
        when(operation.getId()).thenReturn("operation");
        when(leases.acquire(JOB, SETTINGS.getLease())).thenReturn(Mono.just("lease"));
        when(leases.release(JOB, "lease")).thenReturn(Mono.empty());
        when(operations.ensureReconciliationIndex()).thenReturn(Mono.just("index"));
        when(operations.reconciliationBatch(eq(WALLET_OPERATION_STATUS_PENDING), any(), anyInt())).thenReturn(Flux.empty());
        when(operations.reconciliationBatch(eq(WALLET_OPERATION_STATUS_RECEIPT_RECORDED), any(), anyInt())).thenReturn(Flux.just(operation));
        var interruption = new CompletionException(new InterruptedException("interrupted"));
        when(paymentCommands.apply("operation")).thenReturn(Mono.error(new IllegalStateException("apply failed")))
                .thenReturn(Mono.error(interruption));
        when(operations.deferReceiptRecovery(operation)).thenReturn(Mono.empty());

        StepVerifier.create(job.reconcile()).expectComplete().verify(DEADLINE);
        StepVerifier.create(job.reconcile()).expectErrorMatches(error -> error == interruption).verify(DEADLINE);
        verify(operations).deferReceiptRecovery(operation);
    }

    @Test
    void appliesReceiptsDespiteAFailedOrStalledPendingRow() {
        var pending = mock(WalletOperationModel.class);
        var receipt = mock(WalletOperationModel.class);
        when(pending.getId()).thenReturn("pending");
        when(receipt.getId()).thenReturn("receipt");
        when(leases.acquire(JOB, SETTINGS.getLease())).thenReturn(Mono.just("lease"));
        when(leases.release(JOB, "lease")).thenReturn(Mono.empty());
        when(operations.ensureReconciliationIndex()).thenReturn(Mono.just("index"));
        when(operations.reconciliationBatch(eq(WALLET_OPERATION_STATUS_PENDING), any(), anyInt())).thenReturn(Flux.just(pending));
        when(operations.reconciliationBatch(eq(WALLET_OPERATION_STATUS_RECEIPT_RECORDED), any(), anyInt())).thenReturn(Flux.just(receipt));
        when(paymentCommands.markPaymentOutcome(eq("pending"), any())).thenReturn(Mono.error(new IllegalStateException("failed")), Mono.never());
        when(paymentCommands.apply("receipt")).thenReturn(Mono.just(com.playtomic.tests.common.models.Outcome.success(java.util.Optional.of(receipt))));
        StepVerifier.create(job.reconcile()).expectComplete().verify(DEADLINE);
        StepVerifier.create(job.reconcile()).then(() -> verify(paymentCommands, org.mockito.Mockito.times(2)).apply("receipt"))
                .thenCancel().verify(DEADLINE);
    }

    @Test
    void usesConfiguredLeaseBatchLimitAndCutoffs() {
        String token = "lease-token";
        when(leases.acquire(JOB, SETTINGS.getLease())).thenReturn(Mono.just(token));
        when(leases.release(JOB, token)).thenReturn(Mono.empty());
        when(operations.ensureReconciliationIndex()).thenReturn(Mono.just("index"));
        when(operations.reconciliationBatch(any(), any(), anyInt())).thenReturn(Flux.empty());

        Instant start = Instant.now();
        StepVerifier.create(job.reconcile()).expectComplete().verify(DEADLINE);
        Instant end = Instant.now();

        verify(operations).reconciliationBatch(eq(WALLET_OPERATION_STATUS_PENDING), argThat(cutoff ->
                !cutoff.isBefore(start.minus(SETTINGS.getStalePendingAge()))
                        && !cutoff.isAfter(end.minus(SETTINGS.getStalePendingAge()))), eq(SETTINGS.getBatchSize()));
        verify(operations).reconciliationBatch(eq(WALLET_OPERATION_STATUS_RECEIPT_RECORDED), argThat(cutoff ->
                !cutoff.isBefore(start.minus(SETTINGS.getReceiptDelay()))
                        && !cutoff.isAfter(end.minus(SETTINGS.getReceiptDelay()))), eq(SETTINGS.getBatchSize()));
        verify(leases).release(JOB, token);
    }

    @Test
    void reconcilesTerminalEvidenceAndDefersMissingEvidenceWithoutDispatch() {
        var operation = mock(WalletOperationModel.class);
        var reference = java.util.UUID.randomUUID();
        when(operation.getId()).thenReturn("op");
        when(operation.getProvider()).thenReturn("stripe");
        when(operation.getProviderReference()).thenReturn(reference.toString());
        when(operation.getAmount()).thenReturn(java.math.BigDecimal.ONE);
        when(operation.getCurrency()).thenReturn("EUR");
        when(leases.acquire(JOB, SETTINGS.getLease())).thenReturn(Mono.just("lease"));
        when(leases.release(JOB, "lease")).thenReturn(Mono.empty());
        when(operations.ensureReconciliationIndex()).thenReturn(Mono.just("index"));
        when(operations.reconciliationBatch(any(), any(), anyInt())).thenReturn(Flux.empty());
        when(operations.reconciliationBatch(eq(WALLET_OPERATION_STATUS_REVIEW_REQUIRED), any(), anyInt())).thenReturn(Flux.just(operation));
        when(stripe.lookup(reference)).thenReturn(Mono.empty(),
                Mono.just(new StripeChargeEvidence(reference, StripeChargeStatus.DECLINED, null, java.math.BigDecimal.TEN, "EUR")),
                Mono.just(new StripeChargeEvidence(reference, StripeChargeStatus.ACCEPTED, "receipt", java.math.BigDecimal.ONE, "EUR")),
                Mono.just(new StripeChargeEvidence(reference, StripeChargeStatus.DECLINED, null, java.math.BigDecimal.ONE, "EUR")));
        when(operations.deferReview(operation)).thenReturn(Mono.empty());
        when(paymentCommands.recordReceipt("op", "receipt")).thenReturn(Mono.just(com.playtomic.tests.common.models.Outcome.success(java.util.Optional.of(operation))));
        when(operations.transitionTopUp("op", WALLET_OPERATION_STATUS_PAYMENT_REJECTED, WALLET_OPERATION_STATUS_REVIEW_REQUIRED)).thenReturn(Mono.just(operation));
        StepVerifier.create(job.reconcile().repeat(3)).expectComplete().verify(DEADLINE);
        verify(operations, org.mockito.Mockito.times(2)).deferReview(operation);
        verify(paymentCommands).recordReceipt("op", "receipt");
        verify(operations).transitionTopUp("op", WALLET_OPERATION_STATUS_PAYMENT_REJECTED, WALLET_OPERATION_STATUS_REVIEW_REQUIRED);
        verify(stripe, org.mockito.Mockito.times(4)).lookup(reference);
        verifyNoMoreInteractions(stripe);
    }
}