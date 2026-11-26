package com.playtomic.tests.common.configs;

import com.playtomic.tests.common.observability.HttpResponseLoggingFilter;
import com.playtomic.tests.common.observability.StructuredEvents;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in WebFlux diagnostics; the executable supplies its own event logger. */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {
    @Bean
    HttpResponseLoggingFilter httpResponseLoggingFilter(StructuredEvents events) {
        return new HttpResponseLoggingFilter(events);
    }
}

