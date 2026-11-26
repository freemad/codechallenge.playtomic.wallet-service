package com.playtomic.tests.wallet.services.client;

import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.configs.WalletPaymentProviderProps;
import com.playtomic.tests.wallet.dtos.FixtureWire.*;
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
    public Mono<Created> create(Create request) {
        return Mono.defer(() -> http.post(settings.getOrdersUri(), request, HttpStatus.CREATED, null))
                .map(ResponseEntity::getBody).map(body -> new Created(text(body, "id"), text(body, "status")))
                .onErrorMap(ExceptionNormalizer::normalize);
    }

    /** Requests capture of the identified PayPal order. */
    public Mono<Capture> capture(String orderId) {
        return Mono.defer(() -> captureValidated(orderId)).onErrorMap(ExceptionNormalizer::normalize);
    }

    private Mono<Capture> captureValidated(String orderId) {
        if (orderId == null || orderId.isBlank() || ".".equals(orderId) || "..".equals(orderId)) {
            return Mono.error(new IllegalArgumentException("Invalid PayPal order ID"));
        }
        var uri = UriComponentsBuilder.fromUri(settings.getOrdersUri())
                .pathSegment(UriUtils.encodePathSegment(orderId, StandardCharsets.UTF_8), "capture")
                .build(true).toUri();
        return http.post(uri, null, HttpStatus.CREATED, HttpStatus.UNPROCESSABLE_CONTENT).map(response -> {
            var body = response.getBody();
            var units = body.path("purchase_units");
            var captures = units.path(0).path("payments").path("captures");
            boolean rejected = response.getStatusCode().equals(HttpStatus.UNPROCESSABLE_CONTENT);
            if (!rejected && (!units.isArray() || units.size() != 1 || !captures.isArray() || captures.size() != 1)) {
                throw new IllegalStateException("Invalid PayPal capture receipt");
            }
            var capture = captures.path(0);
            var amount = capture.path("amount");
            return new Capture(rejected, text(body, "name"), text(body.path("details").path(0), "issue"),
                    text(body, "id"), text(body, "status"), text(capture, "id"), text(capture, "status"),
                    new Amount(text(amount, "currency_code"), text(amount, "value")));
        });
    }

    private static String text(JsonNode object, String field) {
        var node = object.path(field);
        return node.isString() ? node.asString() : "";
    }
}