package com.playtomic.tests.simulator.controllers;

import com.playtomic.tests.simulator.PaymentSimulatorApplication;
import com.playtomic.tests.simulator.configs.SimulatorProps;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StripeSimulationControllerTest {

    private static final String CHARGE = """
            {"credit_card":"4242424242424242","amount":15.25}
            """;

    @Test
    void returnsTheProviderReceiptAndRejectsMissingFields() throws Exception {
        try (var app = start("SUCCESS"); var client = HttpClient.newHttpClient()) {
            var receipt = charge(app, client, CHARGE);
            assertThat(receipt.statusCode()).isEqualTo(200);
            var json = JsonMapper.builder().build().readTree(receipt.body());
            assertThat(json.size()).isEqualTo(1);
            assertThat(UUID.fromString(json.path("id").asString()).version()).isEqualTo(7);
            assertThat(charge(app, client, "{\"amount\":15.25}").statusCode()).isEqualTo(400);
            assertThat(charge(app, client, CHARGE.replace("15.25", "0")).statusCode()).isEqualTo(400);
        }
    }

    @Test
    void rejectionUsesTheProviderStatusWithoutAReceipt() throws Exception {
        try (var app = start("REJECT"); var client = HttpClient.newHttpClient()) {
            var rejection = charge(app, client, CHARGE);
            assertThat(rejection.statusCode()).isEqualTo(422);
            assertThat(rejection.body()).isEmpty();
        }
    }

    @Test
    void timeoutDoesNotRespondWithinTheWalletsDefaultDeadline() {
        var controller = new StripeSimulationController(new SimulatorProps(SimulatorProps.Scenario.TIMEOUT));
        var request = new StripeSimulationController.ChargeRequest()
                .setCreditCard("4242424242424242")
                .setAmount(new BigDecimal("15.25"));

        StepVerifier.withVirtualTime(() -> controller.charge(request))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(9))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    private static ConfigurableApplicationContext start(String scenario) {
        return new SpringApplicationBuilder(PaymentSimulatorApplication.class)
                .run("--server.port=0", "--payment-simulator.scenario=" + scenario);
    }

    private static HttpResponse<String> charge(ConfigurableApplicationContext app, HttpClient client, String body)
            throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/v1/stripe-simulator/charges"))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}