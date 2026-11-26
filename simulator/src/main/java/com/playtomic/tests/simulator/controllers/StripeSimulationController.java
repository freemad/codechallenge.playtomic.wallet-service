package com.playtomic.tests.simulator.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.contracts.StripeSimulatorMoney;
import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.simulator.configs.SimulatorProps;
import com.playtomic.tests.simulator.models.SimulationResponses;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.Accessors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static com.playtomic.tests.simulator.observability.SimulationOutcome.*;

/** Inbound adapter for the local Stripe-compatible charge fixture. */
@RestController
@RequiredArgsConstructor
public class StripeSimulationController {

    private final SimulatorProps properties;

    /** Checks the card and amount before returning the configured simulated charge outcome. */
    @PostMapping(path = "/v1/stripe-simulator/charges", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Receipt>> charge(@RequestBody ChargeRequest request) {
        if (request.getCreditCard() == null || request.getCreditCard().isBlank()
                || !StripeSimulatorMoney.accepts(request.getAmount())) {
            return STRIPE_INVALID.observe(Mono.just(ResponseEntity.badRequest().build()));
        }
        return switch (properties.getScenario()) {
            case SUCCESS -> RECEIPT_CREATED.observe(Mono.just(ResponseEntity.ok(new Receipt(UuidV7Generator.generate().toString()))));
            case REJECT -> STRIPE_DECLINED.observe(Mono.just(ResponseEntity.unprocessableContent().build()));
            case TIMEOUT -> STRIPE_TIMEOUT.observe(SimulationResponses.timeout());
        };
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(chain = true)
    static class ChargeRequest {
        @JsonProperty(value = "credit_card", access = JsonProperty.Access.WRITE_ONLY)
        private String creditCard;
        private BigDecimal amount;
    }

    @Value
    static class Receipt {
        String id;
    }
}