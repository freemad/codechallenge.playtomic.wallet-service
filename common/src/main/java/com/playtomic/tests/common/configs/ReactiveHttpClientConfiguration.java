package com.playtomic.tests.common.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.WebClient;

/** Provides shared components for reactive outbound HTTP calls. */
@Configuration(proxyBeanMethods = false)
public class ReactiveHttpClientConfiguration {

    @Bean
    NonRetryingWebClientFactory nonRetryingWebClientFactory(WebClient.Builder builder,
                                                            ReactorResourceFactory resources) {
        return new NonRetryingWebClientFactory(builder, resources);
    }
}
