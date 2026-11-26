package com.playtomic.tests.simulator.observability;

import com.playtomic.tests.common.observability.StructuredEvents;
import lombok.RequiredArgsConstructor;
import org.slf4j.event.Level;
import reactor.core.publisher.Mono;

/** Fixture outcomes, not observations of real payments. Never log the fixture input or response. */
@RequiredArgsConstructor
public enum SimulationOutcome {
    STRIPE_INVALID("stripe.charge", "invalid"), RECEIPT_CREATED("stripe.charge", "receipt_created"),
    STRIPE_DECLINED("stripe.charge", "declined"), STRIPE_TIMEOUT("stripe.charge", "timeout_response"),
    ORDER_INVALID("paypal.create", "invalid"), ORDER_LIMIT("paypal.create", "capacity_exhausted"),
    ORDER_CREATED("paypal.create", "order_created"), ORDER_MISSING("paypal.capture", "not_found"),
    CAPTURE_DECLINED("paypal.capture", "declined"), CAPTURE_REPLAY("paypal.capture", "already_captured"),
    CAPTURED("paypal.capture", "captured"), CAPTURE_TIMEOUT("paypal.capture", "timeout_response"),
    REDSYS_INVALID("redsys.authorize", "invalid"), APPROVED("redsys.authorize", "approved"),
    REDSYS_DECLINED("redsys.authorize", "declined"), REDSYS_TIMEOUT("redsys.authorize", "timeout_response");

    public static final StructuredEvents EVENTS = new StructuredEvents("simulator");
    private final String operation;
    private final String result;

    public <T> T record(T response) {
        EVENTS.fields(Level.INFO, "simulation.result").addKeyValue("operation", operation)
                .addKeyValue("result", result).log();
        return response;
    }

    public <T> Mono<T> observe(Mono<T> response) {
        return response.doOnNext(this::record);
    }
}

