package com.playtomic.tests.wallet.services.adapter;

import com.playtomic.tests.common.enums.PayPalOrderState;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.dtos.FixtureWire.Amount;
import com.playtomic.tests.wallet.dtos.FixtureWire.Create;
import com.playtomic.tests.wallet.dtos.FixtureWire.Unit;
import com.playtomic.tests.wallet.models.ProviderPaymentResult;
import com.playtomic.tests.wallet.services.client.PayPalClient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;

/** Creates and captures EUR orders with PayPal. */
@Service
@RequiredArgsConstructor
public final class PayPalAdapter {
    private static final String CURRENCY = "EUR";
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("10000000000");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final PayPalClient client;

    /** Creates a PayPal fixture order for the requested amount without capturing payment. */
    public Mono<Order> createOrder(BigDecimal amount) {
        return Mono.defer(() -> {
            if (amount == null || amount.signum() <= 0 || amount.compareTo(AMOUNT_LIMIT) >= 0
                    || amount.stripTrailingZeros().scale() > 2) {
                return Mono.error(new IllegalArgumentException("PayPal requires a positive EUR amount with at most two decimals"));
            }
            String value = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
            var request = new Create("CAPTURE", List.of(new Unit(new Amount(CURRENCY, value))));
            return client.create(request).map(body -> {
                String id = body.id();
                if (!ID.matcher(id).matches() || !PayPalOrderState.APPROVED.wireValue().equals(body.status())) {
                    throw new IllegalStateException("Invalid PayPal order response");
                }
                return new Order(id, value);
            });
        }).onErrorMap(ExceptionNormalizer::normalize);
    }

    /** Captures the previously created PayPal order and interprets the provider result. */
    public Mono<ProviderPaymentResult> captureOrder(Order order) {
        if (order == null) {
            return Mono.error(() -> ExceptionNormalizer.normalize(new IllegalArgumentException("A PayPal order is required")));
        }
        return Mono.defer(() -> client.capture(order.id).map(response -> {
            if (response.unprocessable()) {
                if ("UNPROCESSABLE_ENTITY".equals(response.name()) && "INSTRUMENT_DECLINED".equals(response.issue())) {
                    return ProviderPaymentResult.declined();
                }
                throw new IllegalStateException("PayPal capture could not be confirmed");
            }
            String id = response.captureId();
            if (!order.id.equals(response.orderId()) || !PayPalOrderState.COMPLETED.wireValue().equals(response.status())
                    || !ID.matcher(id).matches() || order.id.equals(id) || !PayPalOrderState.COMPLETED.wireValue().equals(response.captureStatus())
                    || !CURRENCY.equals(response.amount().currency()) || !order.amount.equals(response.amount().value())) {
                throw new IllegalStateException("Invalid PayPal capture receipt");
            }
            return ProviderPaymentResult.approved(id);
        })).onErrorMap(ExceptionNormalizer::normalize);
    }

    /** Identifies a PayPal order for subsequent capture. */
    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Order {
        String id;
        String amount;
    }
}