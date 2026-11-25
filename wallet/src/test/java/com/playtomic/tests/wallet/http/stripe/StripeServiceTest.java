package com.playtomic.tests.wallet.http.stripe;

import com.playtomic.tests.common.http.NonRetryingWebClientFactory;
import com.playtomic.tests.wallet.configs.StripeSimulatorProps;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerResponse;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class StripeServiceTest {

    private static final String CARD_NUMBER = "4242 4242 4242 4242";
    private static final BigDecimal AMOUNT = new BigDecimal("15.25");
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicReference<CapturedRequest> request = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final ReactorResourceFactory resources = new ReactorResourceFactory();
    private DisposableServer server;

    @BeforeEach
    void startClientResources() {
        resources.setUseGlobalResources(false);
        resources.afterPropertiesSet();
    }

    @AfterEach
    void stopResources() {
        if (server != null) {
            server.disposeNow();
        }
        resources.destroy();
    }

    @Test
    void sendsExactChargeAndReadsReceipt() {
        StripeService service = startProvider(response -> json(response, 200, "{\"id\":\"payment-1\"}"));

        StepVerifier.create(service.charge(CARD_NUMBER, AMOUNT))
                .expectNext(new Payment("payment-1"))
                .expectComplete()
                .verify(TEST_TIMEOUT);

        assertThat(request.get().getMethod()).isEqualTo("POST");
        assertThat(request.get().getUri()).isEqualTo("/charges");
        var body = JsonMapper.builder().build().readTree(request.get().getBody());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("credit_card").asString()).isEqualTo(CARD_NUMBER);
        assertThat(body.path("amount").decimalValue()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void mapsProviderRejection() {
        StripeService service = startProvider(response -> response.status(422).send().then());

        StepVerifier.create(service.charge(CARD_NUMBER, AMOUNT))
                .expectError(StripeAmountTooSmallException.class)
                .verify(TEST_TIMEOUT);
    }

    @Test
    void rejectsAnEmptyReceipt() {
        StripeService service = startProvider(response -> response.status(204).send().then());

        StepVerifier.create(service.charge(CARD_NUMBER, AMOUNT))
                .expectErrorMessage("Payment receipt is missing")
                .verify(TEST_TIMEOUT);
    }

    @Test
    void rejectsAnInvalidReceipt() {
        StripeService service = startProvider(response -> json(response, 200, "{\"id\":\" \"}"));

        StepVerifier.create(service.charge(CARD_NUMBER, AMOUNT))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isExactlyInstanceOf(StripeServiceException.class)
                        .hasCauseInstanceOf(org.springframework.core.codec.CodecException.class))
                .verify(TEST_TIMEOUT);
    }

    @Test
    void timeoutAfterDispatchLeavesTheOutcomeUnconfirmed() {
        StripeService service = startProvider(response -> Mono.never());

        StepVerifier.create(service.charge(CARD_NUMBER, AMOUNT))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isExactlyInstanceOf(StripeServiceException.class)
                        .hasCauseInstanceOf(TimeoutException.class))
                .verify(TEST_TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void lostResponseDoesNotRedispatchTheCharge() {
        StripeService service = startProvider(response -> {
            response.withConnection(Connection::dispose);
            return Mono.empty();
        });

        StepVerifier.create(service.charge(CARD_NUMBER, AMOUNT))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isExactlyInstanceOf(StripeServiceException.class)
                        .hasCauseInstanceOf(org.springframework.web.reactive.function.client.WebClientException.class))
                .verify(TEST_TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void refundsThroughTheConfiguredPaymentPath() {
        StripeService service = startProvider(response -> response.status(204).send().then());

        StepVerifier.create(service.refund("payment/1"))
                .expectComplete()
                .verify(TEST_TIMEOUT);

        assertThat(request.get().getMethod()).isEqualTo("POST");
        assertThat(request.get().getUri()).isEqualTo("/payments/payment%2F1/refunds");
    }

    private StripeService startProvider(Function<HttpServerResponse, Mono<Void>> reply) {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((incoming, outgoing) -> incoming.receive().aggregate().asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            request.set(new CapturedRequest(incoming.method().name(), incoming.uri(), body));
                            calls.incrementAndGet();
                            return reply.apply(outgoing);
                        }))
                .bindNow();
        String baseUrl = "http://127.0.0.1:" + server.port();
        var settings = new StripeSimulatorProps(URI.create(baseUrl + "/charges"),
                baseUrl + "/payments/{payment_id}/refunds", Duration.ofSeconds(1), Duration.ofSeconds(2));
        var factory = new NonRetryingWebClientFactory(WebClient.builder(), resources);
        return new StripeService(settings, factory.create(settings.getConnectTimeout()));
    }

    private static Mono<Void> json(HttpServerResponse response, int status, String body) {
        return response.status(status).header("Content-Type", "application/json")
                .sendString(Mono.just(body)).then();
    }

    @Value
    private static class CapturedRequest {
        String method;
        String uri;
        String body;
    }
}