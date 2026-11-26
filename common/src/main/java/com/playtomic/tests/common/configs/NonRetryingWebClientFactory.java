package com.playtomic.tests.common.configs;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/** Builds HTTP clients with a connection deadline and transport retries disabled. */
public final class NonRetryingWebClientFactory {

    private final WebClient.Builder builder;
    private final ReactorResourceFactory resources;

    /** Uses the supplied builder and Reactor resources to construct non-retrying clients. */
    public NonRetryingWebClientFactory(WebClient.Builder builder, ReactorResourceFactory resources) {
        this.builder = builder;
        this.resources = resources;
    }

    /** Builds a client with the requested connection timeout and transport retries disabled. */
    public WebClient create(Duration connectTimeout) {
        int timeoutMillis = Math.toIntExact(connectTimeout.toMillis());
        return builder.clone()
                .clientConnector(new ReactorClientHttpConnector(resources, client -> client
                        .disableRetry(true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis)))
                .build();
    }
}
