package com.playtomic.tests.wallet.http.stripe;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.configs.NonRetryingWebClientFactory;
import com.playtomic.tests.wallet.configs.WalletPaymentProviderProps;
import com.playtomic.tests.wallet.mappers.StripePaymentMapper;
import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import com.playtomic.tests.wallet.models.PaymentRefundModel;
import com.playtomic.tests.wallet.services.adapter.StripeAdapter;
import com.playtomic.tests.wallet.services.client.StripeClient;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class StripeServiceTest {

    private static final String CARD_NUMBER = "4242 4242 4242 4242";
    private static final BigDecimal AMOUNT = new BigDecimal("15.25");
    private static final CardChargeModel CHARGE = new CardChargeModel(CARD_NUMBER, AMOUNT);
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
        StripeAdapter adapter = startProvider(response -> json(response, 200, "{\"id\":\"payment-1\"}"));

        StepVerifier.create(adapter.charge(CHARGE))
                .expectNext(new PaymentReceiptModel("payment-1"))
                .expectComplete()
                .verify(TEST_TIMEOUT);

        assertThat(request.get().getMethod()).isEqualTo("POST");
        assertThat(request.get().getUri()).isEqualTo("/charges");
        var body = JsonMapper.builder().build().readTree(request.get().getBody());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("credit_card", "amount");
        assertThat(body.path("credit_card").asString()).isEqualTo(CARD_NUMBER);
        assertThat(body.path("amount").isNumber()).isTrue();
        assertThat(body.path("amount").decimalValue()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void normalizesInvalidAdapterInputWithoutDispatch() {
        StripeAdapter adapter = startProvider(response -> response.status(500).send().then());

        StepVerifier.create(adapter.charge(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isExactlyInstanceOf(BusinessException.class)
                            .hasCauseInstanceOf(NullPointerException.class);
                    assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.NULL_POINTER_EXCEPTION);
                })
                .verify(TEST_TIMEOUT);
        assertThat(calls).hasValue(0);
    }

    @Test
    void mapsProviderRejection() {
        StripeAdapter adapter = startProvider(response -> response.status(422).send().then());

        StepVerifier.create(adapter.charge(CHARGE))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(StripeAmountTooSmallException.class)
                            .hasCauseInstanceOf(WebClientResponseException.class);
                    assertThat(((StripeServiceException) error).getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST_EXCEPTION);
                })
                .verify(TEST_TIMEOUT);
    }

    @Test
    void aServerErrorDoesNotConfirmADecline() {
        StripeAdapter adapter = startProvider(response -> response.status(503).send().then());

        StepVerifier.create(adapter.charge(CHARGE))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isExactlyInstanceOf(StripeServiceException.class)
                            .hasCauseInstanceOf(WebClientResponseException.class);
                    assertThat(((StripeServiceException) error).getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_SERVICE_EXCEPTION);
                })
                .verify(TEST_TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsAnEmptyReceipt() {
        StripeAdapter adapter = startProvider(response -> response.status(204).send().then());

        StepVerifier.create(adapter.charge(CHARGE))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isExactlyInstanceOf(StripeServiceException.class);
                    assertThat(((StripeServiceException) error).getAdditionalInfo()).isEqualTo("Payment receipt is missing");
                })
                .verify(TEST_TIMEOUT);
    }

    @Test
    void rejectsAnInvalidReceipt() {
        StripeAdapter adapter = startProvider(response -> json(response, 200, "{\"id\":\" \"}"));

        StepVerifier.create(adapter.charge(CHARGE))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isExactlyInstanceOf(StripeServiceException.class)
                        .hasCauseInstanceOf(org.springframework.core.codec.CodecException.class))
                .verify(TEST_TIMEOUT);
    }

    @Test
    void timeoutAfterDispatchLeavesTheOutcomeUnconfirmed() {
        StripeAdapter adapter = startProvider(response -> Mono.never());

        StepVerifier.create(adapter.charge(CHARGE))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isExactlyInstanceOf(StripeServiceException.class)
                        .hasCauseInstanceOf(TimeoutException.class))
                .verify(TEST_TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void lostResponseDoesNotRedispatchTheCharge() {
        StripeAdapter adapter = startProvider(response -> {
            response.withConnection(Connection::dispose);
            return Mono.empty();
        });

        StepVerifier.create(adapter.charge(CHARGE))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isExactlyInstanceOf(StripeServiceException.class)
                        .hasCauseInstanceOf(org.springframework.web.reactive.function.client.WebClientException.class))
                .verify(TEST_TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void refundsThroughTheConfiguredPaymentPath() {
        StripeAdapter adapter = startProvider(response -> response.status(204).send().then());

        StepVerifier.create(adapter.refund(new PaymentRefundModel("payment/1")))
                .expectComplete()
                .verify(TEST_TIMEOUT);

        assertThat(request.get().getMethod()).isEqualTo("POST");
        assertThat(request.get().getUri()).isEqualTo("/payments/payment%2F1/refunds");
        assertThat(request.get().getBody()).isEmpty();
    }

    private StripeAdapter startProvider(Function<HttpServerResponse, Mono<Void>> reply) {
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
        var stripe = new WalletPaymentProviderProps.Provider(URI.create(baseUrl + "/charges"),
                baseUrl + "/payments/{payment_id}/refunds", null, null, Duration.ofSeconds(1), Duration.ofSeconds(2), true, null);
        var paypal = new WalletPaymentProviderProps.Provider(null, null, URI.create(baseUrl + "/orders"), null,
                stripe.getConnectTimeout(), stripe.getRequestTimeout(), false, null);
        var redsys = new WalletPaymentProviderProps.Provider(null, null, null, URI.create(baseUrl + "/authorization"),
                stripe.getConnectTimeout(), stripe.getRequestTimeout(), false, null);
        var props = new WalletPaymentProviderProps(Map.of(WalletPaymentProviderProps.STRIPE, stripe,
                WalletPaymentProviderProps.PAYPAL, paypal, WalletPaymentProviderProps.REDSYS, redsys));
        var factory = new NonRetryingWebClientFactory(WebClient.builder(), resources);
        return new StripeAdapter(new StripeClient(props, factory.create(stripe.getConnectTimeout())),
                Mappers.getMapper(StripePaymentMapper.class));
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