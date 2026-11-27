package com.playtomic.tests.simulator.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.contracts.StripeChargeEvidence;
import com.playtomic.tests.common.contracts.StripeSimulatorMoney;
import com.playtomic.tests.common.enums.StripeChargeStatus;
import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.simulator.configs.SimulatorProps;
import com.playtomic.tests.simulator.enums.SimulationScenario;
import com.playtomic.tests.simulator.models.SimulationResponses;
import com.playtomic.tests.simulator.services.StripeEvidenceStore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.Accessors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

import static com.playtomic.tests.simulator.enums.SimulationOutcome.*;

/** Inbound adapter for the local Stripe-compatible charge fixture. */
@RestController
@RequiredArgsConstructor
public class StripeSimulationController {

    private final SimulatorProps properties;
    private final StripeEvidenceStore store;

    /** Checks the card and amount before returning the configured simulated charge outcome. */
    @PostMapping(path = "/v1/stripe-simulator/charges", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Receipt>> charge(@RequestBody ChargeRequest request,
            @RequestHeader(value = StripeChargeEvidence.REFERENCE_HEADER, required = false) UUID reference) {
        if (request.getCreditCard() == null || request.getCreditCard().isBlank()
                || !StripeSimulatorMoney.accepts(request.getAmount())) {
            return STRIPE_INVALID.observe(Mono.just(ResponseEntity.badRequest().build()));
        }
        String attemptReceiptId = UuidV7Generator.generate().toString();
        boolean declined = properties.getScenario() == SimulationScenario.REJECT;
        var proposed = new StripeChargeEvidence(reference == null ? UUID.randomUUID() : reference,
                declined ? StripeChargeStatus.DECLINED : StripeChargeStatus.ACCEPTED,
                declined ? null : attemptReceiptId, request.getAmount(), StripeSimulatorMoney.CURRENCY);
        var decision = reference == null ? Mono.just(proposed) : store.record(proposed);
        return decision.flatMap(saved -> chargeResponse(saved, attemptReceiptId))
                .onErrorResume(StripeEvidenceStore.ReferenceConflict.class,
                        conflict -> Mono.just(ResponseEntity.status(409).build()));
    }

    private Mono<ResponseEntity<Receipt>> chargeResponse(StripeChargeEvidence decision, String attemptReceiptId) {
        return switch (decision.status()) {
            case DECLINED -> STRIPE_DECLINED.observe(Mono.just(ResponseEntity.unprocessableContent().<Receipt>build()));
            case ACCEPTED -> {
                // An earlier receipt identifies a replay: return it even under the TIMEOUT scenario.
                boolean newlyAccepted = attemptReceiptId.equals(decision.receiptId());
                if (newlyAccepted && properties.getScenario() == SimulationScenario.TIMEOUT) {
                    yield STRIPE_TIMEOUT.observe(SimulationResponses.<Receipt>timeout());
                }
                yield RECEIPT_CREATED.observe(Mono.just(ResponseEntity.ok(new Receipt(decision.receiptId()))));
            }
        };
    }

    @GetMapping("/v1/stripe-simulator/charges/{reference}")
    public Mono<ResponseEntity<StripeChargeEvidence>> lookup(@PathVariable UUID reference) {
        return store.lookup(reference).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
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