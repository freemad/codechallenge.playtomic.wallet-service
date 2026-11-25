package com.playtomic.tests.simulator;

import com.playtomic.tests.simulator.configs.SimulatorProps;
import com.playtomic.tests.simulator.controllers.PayPalSimulationController;
import com.playtomic.tests.simulator.controllers.RedsysSimulationController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalProviderSimulationTest {

    private static final String PAYPAL = "/v1/paypal-simulator/v2/checkout/orders";
    private static final String REDSYS = "/v1/redsys-simulator/sis/rest/trataPeticion";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORDER = """
            {"intent":"CAPTURE","purchase_units":[{"amount":{"currency_code":"EUR","value":"15.25"}}]}
            """;
    private static final String MERCHANT_PARAMETERS = """
            {"DS_MERCHANT_ORDER":"1234TEST","DS_MERCHANT_AMOUNT":"1525",
             "DS_MERCHANT_CURRENCY":"978","DS_MERCHANT_TRANSACTIONTYPE":"0"}
            """;

    @Test
    void successReturnsProviderReceiptsAndRejectsInvalidRequests() throws Exception {
        try (var app = start("SUCCESS"); var client = HttpClient.newHttpClient()) {
            int port = port(app);
            assertThat(post(client, port, PAYPAL, ORDER.replace("\"15.25\"", "15.25")).statusCode()).isEqualTo(400);
            assertThat(post(client, port, PAYPAL, ORDER.replace("15.25", "15.251")).statusCode()).isEqualTo(400);
            var order = post(client, port, PAYPAL, ORDER);
            assertThat(order.statusCode()).isEqualTo(201);
            assertThat(JSON.readTree(order.body()).path("status").asString()).isEqualTo("APPROVED");
            String orderId = JSON.readTree(order.body()).path("id").asString();
            assertThat(UUID.fromString(orderId).version()).isEqualTo(7);
            var capture = post(client, port, PAYPAL + "/" + orderId + "/capture", "");
            assertThat(capture.statusCode()).isEqualTo(201);
            var receipt = JSON.readTree(capture.body());
            assertThat(receipt.path("id").asString()).isEqualTo(orderId);
            assertThat(receipt.path("status").asString()).isEqualTo("COMPLETED");
            var payment = receipt.path("purchase_units").path(0).path("payments").path("captures").path(0);
            assertThat(UUID.fromString(payment.path("id").asString()).version()).isEqualTo(7);
            assertThat(payment.path("amount").path("currency_code").asString()).isEqualTo("EUR");
            assertThat(payment.path("amount").path("value").asString()).isEqualTo("15.25");
            var duplicate = post(client, port, PAYPAL + "/" + orderId + "/capture", "");
            assertThat(duplicate.statusCode()).isEqualTo(422);
            assertThat(issue(duplicate)).isEqualTo("ORDER_ALREADY_CAPTURED");
            assertThat(post(client, port, PAYPAL + "/missing/capture", "").statusCode()).isEqualTo(404);

            var authorization = post(client, port, REDSYS, redsysBody(encode(MERCHANT_PARAMETERS)));
            assertThat(authorization.statusCode()).isEqualTo(200);
            var parameters = redsysResponse(authorization);
            assertThat(parameters.path("Ds_Order").asString()).isEqualTo("1234TEST");
            assertThat(parameters.path("Ds_Amount").asString()).isEqualTo("1525");
            assertThat(parameters.path("Ds_Response").asString()).isEqualTo("0000");
            assertThat(parameters.path("Ds_AuthorisationCode").asString()).hasSize(6);
            assertThat(post(client, port, REDSYS, redsysBody("!")).statusCode()).isEqualTo(400);
            String invalidAmount = redsysBody(encode(MERCHANT_PARAMETERS.replace("1525", "0")));
            assertThat(post(client, port, REDSYS, invalidAmount).statusCode()).isEqualTo(400);
            assertThat(post(client, port, REDSYS, redsysBody(encode(MERCHANT_PARAMETERS))
                    .replace("SIMULATOR_UNSIGNED", "not-a-fixture-signature")).statusCode()).isEqualTo(400);
        }
    }

    @Test
    void declineUsesPayPalHttpErrorAndRedsysBusinessCode() throws Exception {
        try (var app = start("REJECT"); var client = HttpClient.newHttpClient()) {
            int port = port(app);
            var order = post(client, port, PAYPAL, ORDER);
            assertThat(order.statusCode()).isEqualTo(201);
            String id = JSON.readTree(order.body()).path("id").asString();
            var capture = post(client, port, PAYPAL + "/" + id + "/capture", "");
            assertThat(capture.statusCode()).isEqualTo(422);
            assertThat(issue(capture)).isEqualTo("INSTRUMENT_DECLINED");

            var response = post(client, port, REDSYS, redsysBody(encode(MERCHANT_PARAMETERS)));
            assertThat(response.statusCode()).isEqualTo(200);
            var parameters = redsysResponse(response);
            assertThat(parameters.path("Ds_Response").asString()).isEqualTo("0101");
            assertThat(parameters.has("Ds_AuthorisationCode")).isFalse();
        }
    }

    @Test
    void bothPaymentEndpointsDelayBeforeTimingOut() {
        var properties = new SimulatorProps(SimulatorProps.Scenario.TIMEOUT);
        var paypal = new PayPalSimulationController(properties);
        var order = paypal.create(JSON.readTree(ORDER));
        String id = (String) Objects.requireNonNull(order.getBody()).get("id");
        var redsys = new RedsysSimulationController(properties, JSON);
        var envelope = JSON.readTree(redsysBody(encode(MERCHANT_PARAMETERS)));

        StepVerifier.withVirtualTime(() -> Mono.zip(paypal.capture(id), redsys.authorize(envelope)))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(9))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(results -> {
                    assertThat(results.getT1().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(results.getT2().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                })
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    private static ConfigurableApplicationContext start(String scenario) {
        return new SpringApplicationBuilder(PaymentSimulatorApplication.class)
                .run("--server.port=0", "--payment-simulator.scenario=" + scenario);
    }

    private static int port(ConfigurableApplicationContext app) {
        return Objects.requireNonNull(((WebServerApplicationContext) app).getWebServer()).getPort();
    }

    private static HttpResponse<String> post(HttpClient client, int port, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String issue(HttpResponse<String> response) {
        return JSON.readTree(response.body()).path("details").path(0).path("issue").asString();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String redsysBody(String encoded) {
        return JSON.writeValueAsString(Map.of("Ds_MerchantParameters", encoded,
                "Ds_SignatureVersion", "SIMULATOR_ONLY", "Ds_Signature", "SIMULATOR_UNSIGNED"));
    }

    private static JsonNode redsysResponse(HttpResponse<String> response) {
        var envelope = JSON.readTree(response.body());
        assertThat(envelope.path("Ds_SignatureVersion").asString()).isEqualTo("SIMULATOR_ONLY");
        assertThat(envelope.path("Ds_Signature").asString()).isEqualTo("SIMULATOR_UNSIGNED");
        return JSON.readTree(Base64.getUrlDecoder().decode(envelope.path("Ds_MerchantParameters").asString()));
    }
}
