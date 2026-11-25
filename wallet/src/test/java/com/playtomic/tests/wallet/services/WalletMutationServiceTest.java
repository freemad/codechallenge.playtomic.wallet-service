package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.resilience.ReactiveCircuitBreaker;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.enums.WalletOperationType;
import com.playtomic.tests.wallet.http.stripe.StripeService;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.observability.WalletMutationMetrics;
import com.playtomic.tests.wallet.services.entity.WalletEntityService;
import org.junit.jupiter.api.Test;
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

import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_PENDING;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_RECEIPT_RECORDED;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_SUCCEEDED;
import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @EnumSource(value = WalletOperationType.class, names = {"WALLET_OPERATION_TYPE_TOP_UP", "WALLET_OPERATION_TYPE_SPEND"})
    void returnsTheOperationAsOutcomeData(WalletOperationType type) {
        var result = operation(type, WALLET_OPERATION_STATUS_SUCCEEDED, AMOUNT);

        StepVerifier.create(replay(type, WalletOutcome.optional(Mono.just(result))))
                .assertNext(outcome -> {
                    assertThat(outcome.getData()).isSameAs(result);
                    assertThat(outcome.getError()).isNull();
                })
                .expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest
    @EnumSource(value = WalletOperationType.class, names = {"WALLET_OPERATION_TYPE_TOP_UP", "WALLET_OPERATION_TYPE_SPEND"})
    void propagatesATypedOperationFailure(WalletOperationType type) {
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
    @EnumSource(value = WalletOperationType.class, names = {"WALLET_OPERATION_TYPE_TOP_UP", "WALLET_OPERATION_TYPE_SPEND"})
    void translatesStorageFailureWithoutExposingDiagnostics(WalletOperationType type) {
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
    @EnumSource(value = WalletOperationType.class, names = {"WALLET_OPERATION_TYPE_TOP_UP", "WALLET_OPERATION_TYPE_SPEND"})
    void leavesCancellationOnTheReactiveErrorChannel(WalletOperationType type) {
        var cancellation = new CancellationException();

        StepVerifier.create(replay(type, Mono.error(cancellation)))
                .expectErrorMatches(actual -> actual == cancellation).verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @Test
    void freshTopUpRedactsWalletLookupFailureBeforeReservationOrCharge() {
        var walletEntities = mock(WalletEntityService.class);
        var mutation = new WalletMutationService(new WalletLookupService(walletEntities), operations,
                stripe, mock(ReactiveCircuitBreaker.class), metrics);
        var failure = new DataAccessResourceFailureException("internal wallet lookup diagnostics");
        var code = CommonErrorCode.DATA_RETRIEVE_EXCEPTION;
        when(metrics.mutation(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(operations.find(anyString())).thenReturn(WalletOutcome.optional(Mono.empty()));
        when(walletEntities.findById(WALLET_ID)).thenReturn(Mono.error(failure));

        StepVerifier.create(mutation.topUp(WALLET_ID, "key", AMOUNT, CURRENCY, "unused"))
                .assertNext(outcome -> {
                    assertThat(outcome.isFailure()).isTrue();
                    assertThat(outcome.getError().getCode()).isEqualTo(code);
                    assertThat(outcome.getError().getMessage()).isEqualTo(code.getMessage());
                    assertThat(outcome.getError().getException()).isNull();
                })
                .expectComplete().verify(DEADLINE);
        verify(operations, never()).reserve(any());
        verifyNoInteractions(stripe);
    }

    private Mono<Outcome<WalletOperationModel, Error>> replay(WalletOperationType type,
            Mono<Outcome<Optional<WalletOperationModel>, Error>> applied) {
        var status = type == WALLET_OPERATION_TYPE_TOP_UP ? WALLET_OPERATION_STATUS_RECEIPT_RECORDED : WALLET_OPERATION_STATUS_PENDING;
        when(metrics.mutation(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(operations.find(anyString())).thenReturn(WalletOutcome.optional(Mono.just(operation(type, status, null))));
        when(operations.apply(OPERATION_ID)).thenReturn(applied);
        return type == WALLET_OPERATION_TYPE_TOP_UP ? service.topUp(WALLET_ID, "key", AMOUNT, CURRENCY, "unused")
                : service.spend(WALLET_ID, "key", AMOUNT, CURRENCY);
    }

    private static WalletOperationModel operation(WalletOperationType type, WalletOperationStatus status, BigDecimal balance) {
        return new WalletOperationModel(OPERATION_ID, WALLET_ID, type, AMOUNT, CURRENCY, status, balance, Instant.EPOCH);
    }
}