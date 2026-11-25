package com.playtomic.tests.wallet.services.client;

import com.playtomic.tests.wallet.configs.WalletPaymentProviderProps;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;

@Component
public final class RedsysClient {
    private final ProviderHttpClient http;
    private final WalletPaymentProviderProps.Provider settings;

    RedsysClient(@Qualifier("redsysWebClient") WebClient client, WalletPaymentProviderProps props) {
        this.settings = props.provider(WalletPaymentProviderProps.REDSYS);
        this.http = new ProviderHttpClient(client, settings.getRequestTimeout());
    }

    public Mono<JsonNode> authorize(Map<String, String> envelope) {
        return http.post(settings.getAuthorizationUri(), envelope, HttpStatus.OK, null)
                .map(response -> Objects.requireNonNull(response.getBody()));
    }
}
