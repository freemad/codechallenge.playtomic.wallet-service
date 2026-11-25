package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.services.entity.WalletEntityService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletLookupServiceTest {
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final String ID = "wallet-id";

    private final WalletEntityService repository = mock(WalletEntityService.class);
    private final WalletLookupService service = new WalletLookupService(repository, new WalletOutcomeHandler());

    @Test
    void mapsStorageFailureToStableCodeWithoutExposingDiagnostics() {
        var cause = new DataAccessResourceFailureException("internal storage diagnostics");
        when(repository.findById(ID)).thenReturn(Mono.error(cause));

        assertThat(ExceptionNormalizer.normalize(cause)).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCause()).isSameAs(cause));
        StepVerifier.create(service.findById(ID))
                .assertNext(outcome -> {
                    assertThat(outcome.getData()).isNull();
                    assertThat(outcome.getError().getCode()).isEqualTo(CommonErrorCode.DATA_RETRIEVE_EXCEPTION);
                    assertThat(outcome.getError().getMessage()).isEqualTo(CommonErrorCode.DATA_RETRIEVE_EXCEPTION.getMessage());
                    assertThat(outcome.getError().getException()).isNull();
                })
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void keepsExistingBusinessCodeAndDoesNotWrapTheExceptionAgain() {
        var cause = new IllegalArgumentException("internal detail");
        var failure = new BusinessException(WalletErrorCode.WALLET_NOT_FOUND, "", cause);
        when(repository.findById(ID)).thenReturn(Mono.error(failure));

        assertThat(ExceptionNormalizer.normalize(failure)).isSameAs(failure);
        StepVerifier.create(service.findById(ID))
                .assertNext(outcome -> {
                    assertThat(outcome.getData()).isNull();
                    assertThat(outcome.getError()).isEqualTo(failure.toError());
                })
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void doesNotTurnControlFlowTransportStatusesOrJvmErrorsIntoOutcomes() {
        for (Throwable failure : List.of(new CancellationException(), new InterruptedException(),
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), new AssertionError("unexpected failure"))) {
            when(repository.findById(ID)).thenReturn(Mono.error(failure));
            StepVerifier.create(service.findById(ID))
                    .expectErrorMatches(actual -> actual == failure).verify(DEADLINE);
        }
    }

    @Test
    void mapsAnEmptyReadToWalletNotFound() {
        when(repository.findById(ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.findById(ID))
                .assertNext(outcome -> {
                    assertThat(outcome.isFailure()).isTrue();
                    assertThat(outcome.getError().getCode()).isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
                })
                .expectComplete().verify(DEADLINE);
    }
}