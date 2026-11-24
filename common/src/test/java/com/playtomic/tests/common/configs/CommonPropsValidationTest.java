package com.playtomic.tests.common.configs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommonPropsValidationTest {
    @Test
    void bindsValidOverridesAndPreservesDefaults() {
        try (var context = contextWith(Map.of(
                "application.common.http.connect-timeout-ms", "1",
                "application.common.pagination.default-page-size", "200"))) {
            context.refresh();
            var props = context.getBean(CommonProps.class);

            assertEquals(1, props.getHttp().getConnectTimeoutMs());
            assertEquals(5000, props.getHttp().getResponseTimeoutMs());
            assertEquals(200, props.getPagination().getDefaultPageSize());
            assertEquals(200, props.getPagination().getMaxPageSize());
        }
    }

    @Test
    void bindsObservabilityAndResilienceOverrides() {
        try (var context = contextWith(Map.of(
                "application.common.observability.metric-name-prefix", "playtomic.dev",
                "application.common.resilience.circuit-breaker.failure-rate-threshold", "75",
                "application.common.resilience.circuit-breaker.sliding-window-size", "10",
                "application.common.resilience.circuit-breaker.minimum-number-of-calls", "5",
                "application.common.resilience.circuit-breaker.open-state-wait-ms", "500",
                "application.common.resilience.bulkhead.max-concurrent-calls", "3"))) {
            context.refresh();
            var props = context.getBean(CommonProps.class);
            var breaker = props.getResilience().getCircuitBreaker();

            assertEquals("playtomic.dev", props.getObservability().getMetricNamePrefix());
            assertEquals(75, breaker.getFailureRateThreshold());
            assertEquals(10, breaker.getSlidingWindowSize());
            assertEquals(5, breaker.getMinimumNumberOfCalls());
            assertEquals(500, breaker.getOpenStateWaitMs());
            assertEquals(3, props.getResilience().getBulkhead().getMaxConcurrentCalls());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "http.connect-timeout-ms, 0, http.connectTimeoutMs, Min",
            "http.response-timeout-ms, 0, http.responseTimeoutMs, Min",
            "http.max-in-memory-size-bytes, 0, http.maxInMemorySizeBytes, Min",
            "pagination.default-page-size, 0, pagination.defaultPageSize, Min",
            "pagination.max-page-size, 1001, pagination.maxPageSize, Max",
            "pagination.default-page-size, 201, pagination.defaultPageSizeWithinMaximum, AssertTrue",
            "observability.metric-name-prefix, bad..prefix, observability.metricNamePrefix, Pattern",
            "resilience.circuit-breaker.failure-rate-threshold, 101, resilience.circuitBreaker.failureRateThreshold, Max",
            "resilience.circuit-breaker.open-state-wait-ms, 0, resilience.circuitBreaker.openStateWaitMs, Min",
            "resilience.circuit-breaker.sliding-window-size, 99, resilience.circuitBreaker.minimumNumberOfCallsWithinWindow, AssertTrue",
            "resilience.bulkhead.max-concurrent-calls, 0, resilience.bulkhead.maxConcurrentCalls, Min"
    })
    void rejectsInvalidNestedPropertiesAtStartup(String property, String value, String field, String constraint) {
        try (var context = contextWith(Map.of("application.common." + property, value))) {
            var failure = assertThrows(ConfigurationPropertiesBindException.class, context::refresh);
            var bindFailure = assertInstanceOf(BindException.class, failure.getCause());
            var validation = assertInstanceOf(BindValidationException.class, bindFailure.getCause());

            var errors = validation.getValidationErrors().getAllErrors();
            assertEquals(List.of(constraint), errors.stream().map(ObjectError::getCode).toList());
            assertEquals(field, assertInstanceOf(FieldError.class, errors.getFirst()).getField());
        }
    }

    private static AnnotationConfigApplicationContext contextWith(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(CommonConfig.class);
        return context;
    }
}
