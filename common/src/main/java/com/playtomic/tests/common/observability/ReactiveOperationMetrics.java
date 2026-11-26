package com.playtomic.tests.common.observability;

import com.playtomic.tests.common.configs.CommonProps;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Instruments subscriptions to a named operation using Reactor's Micrometer meters.
 * Use a stable operation name and avoid request-specific tags on the source publisher.
 */
public final class ReactiveOperationMetrics {

    private final MeterRegistry registry;
    private final String operationName;

    /** Creates instrumentation for the named operation in the supplied registry. */
    public ReactiveOperationMetrics(MeterRegistry registry, String operationName) {
        this(registry, operationName, "");
    }

    /** Creates instrumentation for the named operation in the supplied registry. */
    public ReactiveOperationMetrics(MeterRegistry registry, String operationName, CommonProps.Observability settings) {
        this(registry, operationName, settings.getMetricNamePrefix());
    }

    private ReactiveOperationMetrics(MeterRegistry registry, String operationName, String prefix) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("operationName must not be blank");
        }
        this.operationName = prefix.isEmpty() ? operationName : prefix + "." + operationName;
    }

    /** Instruments the source for each subscription without subscribing on the caller's behalf. */
    public <T> Mono<T> measure(Mono<T> source) {
        return source.name(operationName).tap(Micrometer.metrics(registry));
    }

    /** Instruments the source for each subscription without subscribing on the caller's behalf. */
    public <T> Flux<T> measure(Flux<T> source) {
        return source.name(operationName).tap(Micrometer.metrics(registry));
    }
}
