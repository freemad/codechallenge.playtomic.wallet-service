package com.playtomic.tests.simulator.controllers;

import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.simulator.models.SimulationResponses;
import com.playtomic.tests.simulator.configs.SimulatorProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.playtomic.tests.simulator.observability.SimulationOutcome.*;

/** Inbound adapter for a local-only Redsys REST authorization fixture. */
@RestController
@RequiredArgsConstructor
public class RedsysSimulationController {

    private static final String PARAMETERS = "Ds_MerchantParameters";
    private static final String SIGNATURE_VERSION = "Ds_SignatureVersion";
    private static final String SIGNATURE = "Ds_Signature";
    private static final String FIXTURE_VERSION = "SIMULATOR_ONLY";
    private static final String FIXTURE_SIGNATURE = "SIMULATOR_UNSIGNED";
    private static final String CURRENCY = "978";
    private static final String AUTHORIZATION = "0";
    private static final int MAX_ENCODED_LENGTH = 4096;
    private static final Pattern ORDER = Pattern.compile("[0-9]{4}[A-Za-z0-9]{0,8}");
    private static final Pattern AMOUNT = Pattern.compile("[1-9][0-9]{0,11}");

    private final SimulatorProps properties;
    private final JsonMapper json;

    @PostMapping(path = "/v1/redsys-simulator/sis/rest/trataPeticion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, String>>> authorize(@RequestBody JsonNode envelope) {
        String encoded = text(envelope, PARAMETERS);
        if (!FIXTURE_VERSION.equals(text(envelope, SIGNATURE_VERSION))
                || !FIXTURE_SIGNATURE.equals(text(envelope, SIGNATURE))
                || encoded.length() > MAX_ENCODED_LENGTH) {
            return REDSYS_INVALID.observe(Mono.just(ResponseEntity.badRequest().build()));
        }

        JsonNode parameters;
        try {
            parameters = json.readTree(Base64.getUrlDecoder().decode(encoded));
        } catch (IllegalArgumentException | JacksonException invalidInput) {
            return REDSYS_INVALID.observe(Mono.just(ResponseEntity.badRequest().build()));
        }
        String order = text(parameters, "DS_MERCHANT_ORDER");
        String amount = text(parameters, "DS_MERCHANT_AMOUNT");
        if (!ORDER.matcher(order).matches() || !AMOUNT.matcher(amount).matches()
                || !CURRENCY.equals(text(parameters, "DS_MERCHANT_CURRENCY"))
                || !AUTHORIZATION.equals(text(parameters, "DS_MERCHANT_TRANSACTIONTYPE"))) {
            return REDSYS_INVALID.observe(Mono.just(ResponseEntity.badRequest().build()));
        }

        return switch (properties.getScenario()) {
            case SUCCESS -> APPROVED.observe(Mono.just(result(order, amount, true)));
            case REJECT -> REDSYS_DECLINED.observe(Mono.just(result(order, amount, false)));
            case TIMEOUT -> REDSYS_TIMEOUT.observe(SimulationResponses.timeout());
        };
    }

    private ResponseEntity<Map<String, String>> result(String order, String amount, boolean approved) {
        Map<String, String> parameters = new HashMap<>(Map.of("Ds_Order", order, "Ds_Amount", amount,
                "Ds_Currency", CURRENCY, "Ds_TransactionType", AUTHORIZATION,
                "Ds_Response", approved ? "0000" : "0101"));
        if (approved) {
            parameters.put("Ds_AuthorisationCode", UuidV7Generator.generate().toString().substring(24, 30));
        }
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(parameters));
        return ResponseEntity.ok(Map.of(PARAMETERS, encoded, SIGNATURE_VERSION, FIXTURE_VERSION,
                SIGNATURE, FIXTURE_SIGNATURE));
    }

    private static String text(JsonNode object, String field) {
        if (object == null) {
            return "";
        }
        var value = object.path(field);
        return value.isString() ? value.asString() : "";
    }
}