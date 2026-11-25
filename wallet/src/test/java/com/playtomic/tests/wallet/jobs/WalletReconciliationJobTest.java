package com.playtomic.tests.wallet.jobs;

import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.models.WalletOutcome.Completion;
import com.playtomic.tests.wallet.services.WalletJobLeaseService;
import com.playtomic.tests.wallet.services.WalletOperationService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.time.Duration;
import java.util.Optional;

import static com.playtomic.tests.wallet.models.WalletOperationModel.Status.PENDING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalletReconciliationJobTest {
    private static final String JOB = "payment-reconciliation";
    private static final Duration DEADLINE = Duration.ofSeconds(5);

    private final WalletOperationService operations = mock(WalletOperationService.class);
    private final WalletJobLeaseService leases = mock(WalletJobLeaseService.class);
    private final WalletReconciliationJob job = new WalletReconciliationJob(operations, leases);

    @Test
    void skipsWorkWhenTheLeaseIsHeldElsewhere() {
        when(leases.acquire(eq(JOB), any(Duration.class)))
                .thenReturn(Mono.just(Outcome.success(Optional.empty())));

        StepVerifier.create(job.reconcile()).expectComplete().verify(DEADLINE);

        verify(leases).acquire(eq(JOB), any(Duration.class));
        verifyNoInteractions(operations);
        org.mockito.Mockito.verifyNoMoreInteractions(leases);
    }

    @Test
    void releasesTheAcquiredTokenWhenReconciliationIsCancelled() {
        String token = "lease-token";
        var cleanup = PublisherProbe.of(Mono.just(Outcome.<Completion, Error>success(Completion.COMPLETED)));
        when(leases.acquire(eq(JOB), any(Duration.class)))
                .thenReturn(Mono.just(Outcome.success(Optional.of(token))));
        when(operations.ensureReconciliationIndex()).thenReturn(Mono.just(Outcome.success("index")));
        when(operations.reconciliationBatch(any(), any(), anyInt())).thenReturn(Flux.never());
        when(leases.release(JOB, token)).thenReturn(cleanup.mono());

        StepVerifier.create(job.reconcile())
                .then(() -> verify(operations).reconciliationBatch(eq(PENDING), any(), anyInt()))
                .thenCancel().verify(DEADLINE);

        verify(leases).release(JOB, token);
        cleanup.assertWasSubscribed();
    }
}

