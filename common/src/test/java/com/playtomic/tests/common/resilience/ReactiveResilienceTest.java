package com.playtomic.tests.common.resilience;

import com.playtomic.tests.common.configs.CommonProps;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactiveResilienceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void propagatesTheFailureOnceAndRejectsSubsequentSubscriptionsWithoutStartingTheSource() {
        var breaker = CircuitBreaker.of("lookup", CircuitBreakerConfig.custom()
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .build());
        var guard = new ReactiveCircuitBreaker(breaker);
        var subscriptions = new AtomicInteger();
        var failure = new IllegalStateException("lookup failed");
        var guarded = guard.protect(Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return Mono.error(failure);
        }));

        assertEquals(0, subscriptions.get());
        StepVerifier.create(guarded)
                .expectErrorSatisfies(error -> assertSame(failure, error))
                .verify(TIMEOUT);
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        StepVerifier.create(guarded).expectError(CallNotPermittedException.class).verify(TIMEOUT);
        var stream = guard.protect(Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just(1);
        }));
        StepVerifier.create(stream)
                .expectError(CallNotPermittedException.class)
                .verify(TIMEOUT);
        assertEquals(1, subscriptions.get());
    }

    @Test
    void preservesFluxValuesAndCountsTheCompletedStreamAsOneCall() {
        var breaker = CircuitBreaker.ofDefaults("lookup");
        var guard = new ReactiveCircuitBreaker(breaker);

        StepVerifier.create(guard.protect(Flux.just(1, 2)))
                .expectNext(1, 2).expectComplete().verify(TIMEOUT);

        assertEquals(1, breaker.getMetrics().getNumberOfSuccessfulCalls());
    }

    @Test
    void configuredBreakerOpensOnlyWhenTheWindowMeetsTheFailureThreshold() {
        var settings = new CommonProps.Resilience.CircuitBreaker()
                .setSlidingWindowSize(2)
                .setMinimumNumberOfCalls(2)
                .setFailureRateThreshold(100);
        var guard = new ReactiveCircuitBreaker("lookup", settings);
        var failure = new IllegalStateException("lookup failed");
        var failing = guard.protect(Mono.error(failure));
        var succeeding = guard.protect(Mono.just("wallet"));

        StepVerifier.create(failing).expectError(IllegalStateException.class).verify(TIMEOUT);
        StepVerifier.create(succeeding).expectNext("wallet").expectComplete().verify(TIMEOUT);
        StepVerifier.create(failing).expectError(IllegalStateException.class).verify(TIMEOUT);
        StepVerifier.create(failing).expectError(IllegalStateException.class).verify(TIMEOUT);
        StepVerifier.create(succeeding).expectError(CallNotPermittedException.class).verify(TIMEOUT);
    }

    @Test
    void rejectsExcessWorkBeforeSubscriptionAndReleasesThePermitOnCancellation() {
        var bulkhead = new ReactiveBulkhead("lookup", new CommonProps.Resilience.Bulkhead().setMaxConcurrentCalls(1));
        var subscriptions = new AtomicInteger();
        var waiting = bulkhead.protect(Flux.just(1).concatWith(Flux.never()));
        var competing = bulkhead.protect(Mono.fromSupplier(subscriptions::incrementAndGet));

        StepVerifier.create(waiting)
                .expectNext(1)
                .then(() -> StepVerifier.create(competing)
                        .expectError(BulkheadFullException.class).verify(TIMEOUT))
                .thenCancel()
                .verify(TIMEOUT);
        assertEquals(0, subscriptions.get());

        StepVerifier.create(competing).expectNext(1).expectComplete().verify(TIMEOUT);
    }

    @Test
    void releasesThePermitAfterAnErrorOrEmptyCompletion() {
        var bulkhead = new ReactiveBulkhead("lookup", 1);
        var failure = new IllegalStateException("lookup failed");

        StepVerifier.create(bulkhead.protect(Flux.error(failure)))
                .expectErrorSatisfies(error -> assertSame(failure, error))
                .verify(TIMEOUT);
        StepVerifier.create(bulkhead.protect(Mono.empty())).expectComplete().verify(TIMEOUT);
        StepVerifier.create(bulkhead.protect(Mono.just("wallet")))
                .expectNext("wallet").expectComplete().verify(TIMEOUT);
    }

    @Test
    void rejectsInvalidBulkheadConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ReactiveBulkhead(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> new ReactiveBulkhead("lookup", 0));
    }
}
