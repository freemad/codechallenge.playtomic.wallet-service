package com.playtomic.tests.wallet.services.client;

import com.playtomic.tests.wallet.configs.WalletPaymentProviderProps;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Creates and captures orders through the configured PayPal endpoints. */
@Component
public final class PayPalClient {
    private final ProviderHttpClient http;
    private final WalletPaymentProviderProps.Provider settings;

    PayPalClient(@Qualifier("paypalWebClient") WebClient client, WalletPaymentProviderProps props) {
        this.settings = props.provider(WalletPaymentProviderProps.PAYPAL);
        this.http = new ProviderHttpClient(client, settings.getRequestTimeout());
    }

    /** Submits an order to the configured PayPal orders endpoint. */
    public Mono<JsonNode> create(Map<String, ?> request) {
        return http.post(settings.getOrdersUri(), request, HttpStatus.CREATED, null)
                .map(ResponseEntity::getBody);
    }

    /** Requests capture of the identified PayPal order. */
    public Mono<ResponseEntity<JsonNode>> capture(String orderId) {
        if (orderId == null || orderId.isBlank() || ".".equals(orderId) || "..".equals(orderId)) {
            return Mono.error(new IllegalArgumentException("Invalid PayPal order ID"));
        }
        var uri = UriComponentsBuilder.fromUri(settings.getOrdersUri())
                .pathSegment(UriUtils.encodePathSegment(orderId, StandardCharsets.UTF_8), "capture")
                .build(true).toUri();
        return http.post(uri, null, HttpStatus.CREATED, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
