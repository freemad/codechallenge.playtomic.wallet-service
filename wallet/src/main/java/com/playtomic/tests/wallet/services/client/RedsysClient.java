package com.playtomic.tests.wallet.services.client;

import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.configs.WalletPaymentProviderProps;
import com.playtomic.tests.wallet.dtos.FixtureWire.Envelope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;

/** Submits authorization envelopes to the configured Redsys endpoint. */
@Component
public final class RedsysClient {
    private final ProviderHttpClient http;
    private final WalletPaymentProviderProps.Provider settings;

    RedsysClient(@Qualifier("redsysWebClient") WebClient client, WalletPaymentProviderProps props) {
        this.settings = props.provider(WalletPaymentProviderProps.REDSYS);
        this.http = new ProviderHttpClient(client, settings.getRequestTimeout());
    }

    /** Posts the authorization envelope to the configured Redsys endpoint. */
    public Mono<Envelope> authorize(Envelope envelope) {
        return Mono.defer(() -> http.post(settings.getAuthorizationUri(), envelope, HttpStatus.OK, null))
                .map(response -> Objects.requireNonNull(response.getBody()))
                .map(body -> new Envelope(text(body, "Ds_MerchantParameters"), text(body, "Ds_SignatureVersion"), text(body, "Ds_Signature")))
                .onErrorMap(ExceptionNormalizer::normalize);
    }

    private static String text(JsonNode body, String field) {
        var node = body.path(field);
        return node.isString() ? node.asString() : "";
    }
}