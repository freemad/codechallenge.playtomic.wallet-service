package com.playtomic.tests.simulator.controllers;

import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.simulator.configs.SimulatorProps;
import com.playtomic.tests.simulator.models.SimulationResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.playtomic.tests.simulator.observability.SimulationOutcome.*;

/** Inbound adapter for the local PayPal Orders fixture. */
@RestController
@RequestMapping("/v1/paypal-simulator/v2/checkout/orders")
@RequiredArgsConstructor
public class PayPalSimulationController {

    private static final String CURRENCY = "EUR";
    private static final int MAX_ORDERS = 1024;
    private static final Pattern DECIMAL_AMOUNT = Pattern.compile("[0-9]{1,10}(\\.[0-9]{1,2})?");

    private final SimulatorProps properties;
    private final ConcurrentMap<String, Order> orders = new ConcurrentHashMap<>();
    // Keep captured orders until restart so a replay can never create another capture.
    private final Semaphore capacity = new Semaphore(MAX_ORDERS);

    /** Validates the order request and returns the configured fixture response. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, ?>> create(@RequestBody JsonNode request) {
        var units = request.path("purchase_units");
        if (!"CAPTURE".equals(request.path("intent").asString()) || !units.isArray() || units.size() != 1) {
            return ORDER_INVALID.record(error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        }
        var amount = units.path(0).path("amount");
        var value = amount.path("value");
        if (!CURRENCY.equals(amount.path("currency_code").asString()) || !value.isString()
                || !DECIMAL_AMOUNT.matcher(value.asString()).matches()) {
            return ORDER_INVALID.record(error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        }
        var decimal = new BigDecimal(value.asString());
        if (decimal.signum() <= 0) {
            return ORDER_INVALID.record(error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        }
        if (!capacity.tryAcquire()) {
            return ORDER_LIMIT.record(error(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_LIMIT_REACHED"));
        }

        String id = UuidV7Generator.generate().toString();
        orders.put(id, new Order(decimal.setScale(2).toPlainString()));
        return ORDER_CREATED.record(ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", "APPROVED")));
    }

    /** Simulates capture of the identified PayPal order. */
    @PostMapping("/{orderId}/capture")
    public Mono<ResponseEntity<Map<String, ?>>> capture(@PathVariable String orderId) {
        var order = orders.get(orderId);
        if (order == null) {
            return ORDER_MISSING.observe(Mono.just(error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND")));
        }
        return switch (properties.getScenario()) {
            case SUCCESS -> Mono.fromSupplier(() -> complete(orderId, order));
            case REJECT -> CAPTURE_DECLINED.observe(Mono.just(captureError("INSTRUMENT_DECLINED")));
            case TIMEOUT -> CAPTURE_TIMEOUT.observe(SimulationResponses.timeout());
        };
    }

    private static ResponseEntity<Map<String, ?>> complete(String orderId, Order order) {
        if (!order.captured.compareAndSet(false, true)) {
            return CAPTURE_REPLAY.record(captureError("ORDER_ALREADY_CAPTURED"));
        }
        var capture = Map.of("id", UuidV7Generator.generate().toString(), "status", "COMPLETED",
                "amount", Map.of("currency_code", CURRENCY, "value", order.amount));
        var unit = Map.of("payments", Map.of("captures", List.of(capture)));
        return CAPTURED.record(ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", orderId, "status", "COMPLETED", "purchase_units", List.of(unit))));
    }

    private static ResponseEntity<Map<String, ?>> error(HttpStatus status, String name) {
        return ResponseEntity.status(status).body(Map.of("name", name));
    }

    private static ResponseEntity<Map<String, ?>> captureError(String issue) {
        return ResponseEntity.unprocessableContent()
                .body(Map.of("name", "UNPROCESSABLE_ENTITY", "details", List.of(Map.of("issue", issue))));
    }

    @RequiredArgsConstructor
    private static class Order {
        private final String amount;
        private final AtomicBoolean captured = new AtomicBoolean();
    }
}