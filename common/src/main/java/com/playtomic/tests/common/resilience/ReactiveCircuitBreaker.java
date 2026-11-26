package com.playtomic.tests.common.resilience;

import com.playtomic.tests.common.configs.CommonProps;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/** Applies a caller-configured circuit breaker to cold publishers without retries or fallbacks. */
public final class ReactiveCircuitBreaker {

    private final CircuitBreaker circuitBreaker;

    /** Creates a subscription-time guard backed by the configured circuit breaker. */
    public ReactiveCircuitBreaker(String name, CommonProps.Resilience.CircuitBreaker settings) {
        this(CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .failureRateThreshold(settings.getFailureRateThreshold())
                .slidingWindowSize(settings.getSlidingWindowSize())
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(settings.getOpenStateWaitMs()))
                .build()));
    }

    /** Creates a subscription-time guard backed by the configured circuit breaker. */
    public ReactiveCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
    }

    /** Applies the circuit breaker separately to each subscription. */
    public <T> Mono<T> protect(Mono<T> source) {
        return source.transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    /** Applies the circuit breaker separately to each subscription. */
    public <T> Flux<T> protect(Flux<T> source) {
        return source.transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}
