package com.playtomic.tests.common.http;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

public final class NonRetryingWebClientFactory {

    private final WebClient.Builder builder;
    private final ReactorResourceFactory resources;

    public NonRetryingWebClientFactory(WebClient.Builder builder, ReactorResourceFactory resources) {
        this.builder = builder;
        this.resources = resources;
    }

    public WebClient create(Duration connectTimeout) {
        int timeoutMillis = Math.toIntExact(connectTimeout.toMillis());
        return builder.clone()
                .clientConnector(new ReactorClientHttpConnector(resources, client -> client
                        .disableRetry(true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis)))
                .build();
    }
}
