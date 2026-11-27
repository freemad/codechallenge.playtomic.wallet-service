package com.playtomic.tests.simulator.controllers;

import com.playtomic.tests.common.contracts.StripeChargeEvidence;
import com.playtomic.tests.simulator.PaymentSimulatorApplication;
import com.playtomic.tests.simulator.configs.SimulatorProps;
import com.playtomic.tests.simulator.services.StripeEvidenceStore;
import com.playtomic.tests.common.enums.StripeChargeStatus;
import com.playtomic.tests.simulator.enums.SimulationScenario;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
    @ExtendWith(OutputCaptureExtension.class)
    void returnsTheProviderReceiptAndRejectsMissingFields(CapturedOutput output) throws Exception {
        try (var app = start("SUCCESS"); var client = HttpClient.newHttpClient()) {
            var receipt = charge(app, client, CHARGE);
            assertThat(receipt.statusCode()).isEqualTo(200);
            var json = JsonMapper.builder().build().readTree(receipt.body());
            assertThat(json.size()).isEqualTo(1);
            assertThat(UUID.fromString(json.path("id").asString()).version()).isEqualTo(7);
            assertThat(charge(app, client, "{\"amount\":15.25}").statusCode()).isEqualTo(400);
            assertThat(charge(app, client, CHARGE.replace("15.25", "0")).statusCode()).isEqualTo(400);
            var mapper = JsonMapper.builder().build();
            var events = output.getOut().lines().filter(line -> line.startsWith("{"))
                    .map(mapper::readTree).filter(node -> "simulator.events".equals(node.path("logger_name").asString(""))).toList();
            assertThat(events.stream().map(node -> node.path("event").asString())).containsExactly(
                    "simulation.result", "http.response", "simulation.result", "http.response", "simulation.result", "http.response");
            assertThat(events.getFirst().path("result").asString()).isEqualTo("receipt_created");
            assertThat(events.toString()).doesNotContain("4242424242424242", "\"amount\"", json.path("id").asString(), "/v1/stripe-simulator");
            assertThat(app.getBean(MeterRegistry.class).get("http.server.requests").tag("status", "200").timer().count()).isEqualTo(1);
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
        var controller = new StripeSimulationController(new SimulatorProps(SimulationScenario.TIMEOUT), org.mockito.Mockito.mock(StripeEvidenceStore.class));
        var request = new StripeSimulationController.ChargeRequest()
                .setCreditCard("4242424242424242")
                .setAmount(new BigDecimal("15.25"));

        StepVerifier.withVirtualTime(() -> controller.charge(request, null))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(9))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @ParameterizedTest
    @EnumSource(SimulationScenario.class)
    void acceptedReplayReturnsOriginalReceiptRegardlessOfScenario(SimulationScenario scenario) {
        var store = org.mockito.Mockito.mock(StripeEvidenceStore.class);
        var reference = UUID.randomUUID();
        var accepted = new StripeChargeEvidence(reference, StripeChargeStatus.ACCEPTED, "original", BigDecimal.ONE, "EUR");
        org.mockito.Mockito.when(store.record(org.mockito.ArgumentMatchers.any())).thenReturn(reactor.core.publisher.Mono.just(accepted));
        var controller = new StripeSimulationController(new SimulatorProps(scenario), store);
        var request = new StripeSimulationController.ChargeRequest().setCreditCard("4242424242424242").setAmount(BigDecimal.ONE);
        StepVerifier.create(controller.charge(request, reference)).assertNext(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getId()).isEqualTo("original");
        }).verifyComplete();
    }

    private static ConfigurableApplicationContext start(String scenario) {
        return new SpringApplicationBuilder(PaymentSimulatorApplication.class)
                .run("--server.port=0", "--logging.level.simulator.events=DEBUG", "--payment-simulator.scenario=" + scenario);
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