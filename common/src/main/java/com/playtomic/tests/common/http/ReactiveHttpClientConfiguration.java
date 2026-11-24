package com.playtomic.tests.common.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class ReactiveHttpClientConfiguration {

    @Bean
    NonRetryingWebClientFactory nonRetryingWebClientFactory(WebClient.Builder builder,
                                                             ReactorResourceFactory resources) {
        return new NonRetryingWebClientFactory(builder, resources);
    }
}
