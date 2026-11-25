package com.playtomic.tests.wallet.services.adapter;

import com.playtomic.tests.wallet.models.ProviderPaymentResult;
import com.playtomic.tests.wallet.services.client.PayPalClient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Creates and captures EUR orders with PayPal. */
@Service
@RequiredArgsConstructor
public final class PayPalAdapter {
    private static final String CURRENCY = "EUR";
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("10000000000");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final PayPalClient client;

    public Mono<Order> createOrder(BigDecimal amount) {
        return Mono.defer(() -> {
            if (amount == null || amount.signum() <= 0 || amount.compareTo(AMOUNT_LIMIT) >= 0
                    || amount.stripTrailingZeros().scale() > 2) {
                return Mono.error(new IllegalArgumentException("PayPal requires a positive EUR amount with at most two decimals"));
            }
            String value = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
            var request = Map.of("intent", "CAPTURE", "purchase_units",
                    List.of(Map.of("amount", Map.of("currency_code", CURRENCY, "value", value))));
            return client.create(request).map(body -> {
                String id = text(body, "id");
                if (!ID.matcher(id).matches() || !"APPROVED".equals(text(body, "status"))) {
                    throw new IllegalStateException("Invalid PayPal order response");
                }
                return new Order(id, value);
            });
        });
    }

    public Mono<ProviderPaymentResult> captureOrder(Order order) {
        if (order == null) {
            return Mono.error(new IllegalArgumentException("A PayPal order is required"));
        }
        return Mono.defer(() -> client.capture(order.id).map(response -> {
            var body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("Missing PayPal capture response");
            }
            if (response.getStatusCode().equals(HttpStatus.UNPROCESSABLE_CONTENT)) {
                if ("UNPROCESSABLE_ENTITY".equals(text(body, "name"))
                        && "INSTRUMENT_DECLINED".equals(text(body.path("details").path(0), "issue"))) {
                    return ProviderPaymentResult.declined();
                }
                throw new IllegalStateException("PayPal capture could not be confirmed");
            }
            var units = body.path("purchase_units");
            var captures = units.path(0).path("payments").path("captures");
            var capture = captures.path(0);
            var amount = capture.path("amount");
            String id = text(capture, "id");
            if (!order.id.equals(text(body, "id")) || !"COMPLETED".equals(text(body, "status"))
                    || !units.isArray() || units.size() != 1 || !captures.isArray() || captures.size() != 1
                    || !ID.matcher(id).matches() || order.id.equals(id) || !"COMPLETED".equals(text(capture, "status"))
                    || !CURRENCY.equals(text(amount, "currency_code")) || !order.amount.equals(text(amount, "value"))) {
                throw new IllegalStateException("Invalid PayPal capture receipt");
            }
            return ProviderPaymentResult.approved(id);
        }));
    }

    private static String text(JsonNode object, String field) {
        var value = object.path(field);
        return value.isString() ? value.asString() : "";
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Order {
        String id;
        String amount;
    }
}
