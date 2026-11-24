package com.playtomic.tests.common.observability;

import com.playtomic.tests.common.configs.CommonProps;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveOperationMetricsTest {

    private static final String OPERATION = "wallet.lookup";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final ReactiveOperationMetrics metrics = new ReactiveOperationMetrics(registry, OPERATION);

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void timesEachMonoSubscriptionWithoutSubscribingDuringAssembly() {
        var measured = metrics.measure(Mono.fromSupplier(() -> {
            clock.add(Duration.ofMillis(25));
            return "wallet";
        }));

        assertTrue(registry.find(OPERATION + ".flow.duration").timers().isEmpty());
        StepVerifier.create(measured).expectNext("wallet").expectComplete().verify(TIMEOUT);
        StepVerifier.create(measured).expectNext("wallet").expectComplete().verify(TIMEOUT);

        var duration = timer("Mono", "completed", "");
        assertEquals(2L, duration.count());
        assertEquals(50.0, duration.totalTime(TimeUnit.MILLISECONDS), 0.001);
    }

    @Test
    void prefixesTheMeterWhenConfigured() {
        var settings = new CommonProps.Observability().setMetricNamePrefix("playtomic.dev");
        var configured = new ReactiveOperationMetrics(registry, OPERATION, settings);

        StepVerifier.create(configured.measure(Mono.just("wallet")))
                .expectNext("wallet").expectComplete().verify(TIMEOUT);

        assertEquals(1L, registry.get("playtomic.dev." + OPERATION + ".flow.duration").timer().count());
        assertTrue(registry.find(OPERATION + ".flow.duration").timers().isEmpty());
    }

    @Test
    void distinguishesEmptyCompletionFromAValue() {
        StepVerifier.create(metrics.measure(Mono.empty())).expectComplete().verify(TIMEOUT);
        StepVerifier.create(metrics.measure(Flux.empty())).expectComplete().verify(TIMEOUT);

        assertEquals(1L, timer("Mono", "completedEmpty", "").count());
        assertEquals(1L, timer("Flux", "completedEmpty", "").count());
    }

    @Test
    void preservesFluxValuesAndRecordsOneCompletionForTheStream() {
        StepVerifier.create(metrics.measure(Flux.just(1, 2)))
                .expectNext(1, 2)
                .expectComplete()
                .verify(TIMEOUT);

        assertEquals(1L, timer("Flux", "completed", "").count());
    }

    @Test
    void propagatesErrorsAndRecordsTheExceptionTypeNotItsMessage() {
        var failure = new IllegalStateException("request-specific error details");
        StepVerifier.create(metrics.measure(Flux.concat(Flux.just(1), Flux.error(failure))))
                .expectNext(1)
                .expectErrorSatisfies(error -> assertSame(failure, error))
                .verify(TIMEOUT);

        assertEquals(1L, timer("Flux", "error", IllegalStateException.class.getName()).count());
    }

    @Test
    void recordsCancellationWithoutChangingDemandOrCompletion() {
        StepVerifier.create(metrics.measure(Flux.just(1, 2)), 0)
                .thenRequest(1)
                .expectNext(1)
                .then(() -> clock.add(Duration.ofMillis(10)))
                .thenCancel()
                .verify(TIMEOUT);

        var duration = timer("Flux", "cancelled", "");
        assertEquals(1L, duration.count());
        assertEquals(10.0, duration.totalTime(TimeUnit.MILLISECONDS), 0.001);
        assertEquals(1, registry.find(OPERATION + ".flow.duration").timers().size());
    }

    @Test
    void rejectsMissingRegistryOrOperationName() {
        assertThrows(NullPointerException.class, () -> new ReactiveOperationMetrics(null, OPERATION));
        assertThrows(IllegalArgumentException.class, () -> new ReactiveOperationMetrics(registry, " "));
    }

    private Timer timer(String type, String status, String exception) {
        return registry.get(OPERATION + ".flow.duration")
                .tags("type", type, "status", status, "exception", exception)
                .timer();
    }
}
