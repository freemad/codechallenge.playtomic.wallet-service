package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.resilience.ReactiveCircuitBreaker;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.http.stripe.StripeService;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.models.WalletOperationModel.Status;
import com.playtomic.tests.wallet.models.WalletOperationModel.Type;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.observability.WalletMutationMetrics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalletMutationServiceTest {
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final String WALLET_ID = "wallet-id";
    private static final String OPERATION_ID = "operation-id";
    private static final String CURRENCY = "EUR";
    private static final BigDecimal AMOUNT = new BigDecimal("5.00");

    private final WalletOperationService operations = mock(WalletOperationService.class);
    private final StripeService stripe = mock(StripeService.class);
    private final WalletMutationMetrics metrics = mock(WalletMutationMetrics.class);
    private final WalletMutationService service = new WalletMutationService(mock(WalletLookupService.class), operations,
            stripe, mock(ReactiveCircuitBreaker.class), metrics);

    @ParameterizedTest
    @EnumSource(Type.class)
    void returnsTheOperationAsOutcomeData(Type type) {
        var result = operation(type, Status.SUCCEEDED, AMOUNT);

        StepVerifier.create(replay(type, WalletOutcome.optional(Mono.just(result))))
                .assertNext(outcome -> {
                    assertThat(outcome.getData()).isSameAs(result);
                    assertThat(outcome.getError()).isNull();
                })
                .expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void propagatesATypedOperationFailure(Type type) {
        var code = WalletErrorCode.IDEMPOTENCY_CONFLICT;
        var failure = new Error().setCode(code).setMessage(code.getMessage());

        StepVerifier.create(replay(type, Mono.just(Outcome.failure(failure))))
                .assertNext(outcome -> {
                    assertThat(outcome.isFailure()).isTrue();
                    assertThat(outcome.getError().getCode()).isEqualTo(code);
                })
                .expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void translatesStorageFailureWithoutExposingDiagnostics(Type type) {
        var failure = new DataAccessResourceFailureException("internal storage diagnostics");

        StepVerifier.create(replay(type, Mono.error(failure)))
                .assertNext(outcome -> {
                    assertThat(outcome.getData()).isNull();
                    assertThat(outcome.getError().getCode()).isEqualTo(CommonErrorCode.DATA_RETRIEVE_EXCEPTION);
                    assertThat(outcome.getError().getMessage()).isEqualTo(CommonErrorCode.DATA_RETRIEVE_EXCEPTION.getMessage());
                    assertThat(outcome.getError().getException()).isNull();
                })
                .expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void leavesCancellationOnTheReactiveErrorChannel(Type type) {
        var cancellation = new CancellationException();

        StepVerifier.create(replay(type, Mono.error(cancellation)))
                .expectErrorMatches(actual -> actual == cancellation).verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    private Mono<Outcome<WalletOperationModel, Error>> replay(Type type,
            Mono<Outcome<Optional<WalletOperationModel>, Error>> applied) {
        var status = type == Type.TOP_UP ? Status.RECEIPT_RECORDED : Status.PENDING;
        when(metrics.mutation(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(operations.find(anyString())).thenReturn(WalletOutcome.optional(Mono.just(operation(type, status, null))));
        when(operations.apply(OPERATION_ID)).thenReturn(applied);
        return type == Type.TOP_UP ? service.topUp(WALLET_ID, "key", AMOUNT, CURRENCY, "unused")
                : service.spend(WALLET_ID, "key", AMOUNT, CURRENCY);
    }

    private static WalletOperationModel operation(Type type, Status status, BigDecimal balance) {
        return new WalletOperationModel(OPERATION_ID, WALLET_ID, type, AMOUNT, CURRENCY, status, balance, Instant.EPOCH);
    }
}