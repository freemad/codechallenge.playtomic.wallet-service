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

    public ReactiveCircuitBreaker(String name, CommonProps.Resilience.CircuitBreaker settings) {
        this(CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .failureRateThreshold(settings.getFailureRateThreshold())
                .slidingWindowSize(settings.getSlidingWindowSize())
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(settings.getOpenStateWaitMs()))
                .build()));
    }

    public ReactiveCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
    }

    public <T> Mono<T> protect(Mono<T> source) {
        return source.transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    public <T> Flux<T> protect(Flux<T> source) {
        return source.transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}
