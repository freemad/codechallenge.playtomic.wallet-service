package com.playtomic.tests.wallet.services.adapter;

import com.playtomic.tests.common.enums.RedsysAuthorizationState;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.dtos.FixtureWire.Authorization;
import com.playtomic.tests.wallet.dtos.FixtureWire.Envelope;
import com.playtomic.tests.wallet.models.ProviderPaymentResult;
import com.playtomic.tests.wallet.services.client.RedsysClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.regex.Pattern;

/** Authorizes EUR amounts using the unsigned Redsys test protocol. */
@Service
@RequiredArgsConstructor
public final class RedsysAdapter {
    private static final String PARAMETERS = "Ds_MerchantParameters";
    private static final String VERSION = "Ds_SignatureVersion";
    private static final String SIGNATURE = "Ds_Signature";
    private static final String FIXTURE_VERSION = "SIMULATOR_ONLY";
    private static final String FIXTURE_SIGNATURE = "SIMULATOR_UNSIGNED";
    private static final String CURRENCY = "978";
    private static final String AUTHORIZATION = "0";
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("10000000000");
    private static final Pattern ORDER = Pattern.compile("[0-9]{4}[A-Za-z0-9]{0,8}");
    private static final Pattern AUTHORIZATION_CODE = Pattern.compile("[0-9a-f]{6}");

    private final RedsysClient client;
    private final JsonMapper json;

    /** Builds a Redsys authorization for the order and interprets the payment result. */
    public Mono<ProviderPaymentResult> authorize(String order, BigDecimal amount) {
        return Mono.defer(() -> {
            if (order == null || !ORDER.matcher(order).matches()) {
                return Mono.error(new IllegalArgumentException("Invalid Redsys order"));
            }
            if (amount == null || amount.signum() <= 0 || amount.compareTo(AMOUNT_LIMIT) >= 0
                    || amount.stripTrailingZeros().scale() > 2) {
                return Mono.error(new IllegalArgumentException("Redsys requires a positive EUR amount with at most two decimals"));
            }
            String minorUnits = amount.movePointRight(2).toBigIntegerExact().toString();
            var parameters = new Authorization(order, minorUnits, CURRENCY, AUTHORIZATION);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(parameters));
            return client.authorize(new Envelope(encoded, FIXTURE_VERSION, FIXTURE_SIGNATURE))
                    .map(envelope -> result(envelope, order, minorUnits));
        }).onErrorMap(ExceptionNormalizer::normalize);
    }

    private ProviderPaymentResult result(Envelope envelope, String order, String minorUnits) {
        String encoded = envelope.parameters();
        if (!FIXTURE_VERSION.equals(envelope.version()) || !FIXTURE_SIGNATURE.equals(envelope.signature())
                || encoded.isEmpty() || encoded.length() > 4096) {
            throw new IllegalStateException("Invalid Redsys envelope");
        }
        JsonNode parameters;
        try {
            parameters = json.readTree(Base64.getUrlDecoder().decode(encoded));
        } catch (IllegalArgumentException | JacksonException error) {
            throw new IllegalStateException("Invalid Redsys parameters", error);
        }
        if (parameters == null || !parameters.isObject()) {
            throw new IllegalStateException("Invalid Redsys parameters");
        }
        if (!order.equals(text(parameters, "Ds_Order")) || !minorUnits.equals(text(parameters, "Ds_Amount"))
                || !CURRENCY.equals(text(parameters, "Ds_Currency"))
                || !AUTHORIZATION.equals(text(parameters, "Ds_TransactionType"))) {
            throw new IllegalStateException("Redsys response does not match the request");
        }
        String code = text(parameters, "Ds_AuthorisationCode");
        String response = text(parameters, "Ds_Response");
        if (RedsysAuthorizationState.DECLINED.wireValue().equals(response) && !parameters.has("Ds_AuthorisationCode")) {
            return ProviderPaymentResult.declined();
        }
        if (RedsysAuthorizationState.APPROVED.wireValue().equals(response) && AUTHORIZATION_CODE.matcher(code).matches()) {
            return ProviderPaymentResult.approved(code);
        }
        throw new IllegalStateException("Redsys authorization could not be confirmed");
    }

    private static String text(JsonNode object, String field) {
        var value = object.path(field);
        return value.isString() ? value.asString() : "";
    }
}