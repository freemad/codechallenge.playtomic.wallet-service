package com.playtomic.tests.common.resilience;

import com.playtomic.tests.common.configs.CommonProps;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Limits concurrent subscriptions to cold publishers without waiting for a permit. */
public final class ReactiveBulkhead {

    private final Bulkhead bulkhead;

    /** Creates a subscription-time guard limiting concurrent calls. */
    public ReactiveBulkhead(String name, CommonProps.Resilience.Bulkhead settings) {
        this(name, settings.getMaxConcurrentCalls());
    }

    /** Creates a subscription-time guard limiting concurrent calls. */
    public ReactiveBulkhead(String name, int maxConcurrentCalls) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("maxConcurrentCalls must be positive");
        }
        this.bulkhead = Bulkhead.of(name, BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(Duration.ZERO)
                .build());
    }

    /** Applies the concurrency limit separately to each subscription. */
    public <T> Mono<T> protect(Mono<T> source) {
        return source.transformDeferred(BulkheadOperator.of(bulkhead));
    }

    /** Applies the concurrency limit separately to each subscription. */
    public <T> Flux<T> protect(Flux<T> source) {
        return source.transformDeferred(BulkheadOperator.of(bulkhead));
    }
}
