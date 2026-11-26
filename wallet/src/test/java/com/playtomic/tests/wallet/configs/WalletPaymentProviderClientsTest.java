package com.playtomic.tests.wallet.configs;

import com.playtomic.tests.common.configs.CommonProps;
import com.playtomic.tests.common.configs.ReactiveHttpClientConfiguration;
import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.http.stripe.StripeService;
import com.playtomic.tests.wallet.mappers.StripePaymentMapper;
import com.playtomic.tests.wallet.models.ProviderPaymentResult;
import com.playtomic.tests.wallet.services.WalletTopUpProviders;
import com.playtomic.tests.wallet.services.adapter.PayPalAdapter;
import com.playtomic.tests.wallet.services.adapter.RedsysAdapter;
import com.playtomic.tests.wallet.services.adapter.StripeAdapter;
import com.playtomic.tests.wallet.services.client.PayPalClient;
import com.playtomic.tests.wallet.services.client.StripeClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerResponse;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WalletPaymentProviderClientsTest {
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final BigDecimal AMOUNT = new BigDecimal("15.25");
    private static final String ORDER = "1234AB";
    private static final String CREATED = "{\"id\":\"order-1\",\"status\":\"APPROVED\"}";
    private static final String CAPTURED = """
            {"id":"order-1","status":"COMPLETED","purchase_units":[{"payments":{"captures":[
              {"id":"capture-1","status":"COMPLETED","amount":{"currency_code":"EUR","value":"15.25"}}]}}]}
            """;

    private final JsonMapper json = JsonMapper.builder().build();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ClientProperties.class, WalletPaymentConfiguration.class,
                    ReactiveHttpClientConfiguration.class, StripeService.class,
                    PayPalAdapter.class, RedsysAdapter.class, StripeAdapter.class)
            .withBean(JsonMapper.class, () -> json)
            .withBean(WebClient.Builder.class, WebClient::builder)
            .withBean(ReactorResourceFactory.class, () -> {
                var resources = new ReactorResourceFactory();
                resources.setUseGlobalResources(false);
                return resources;
            });

    @Test
    void bindsProviderDefaultsAndWiresAllThreeClients() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(StripeService.class)
                    .hasSingleBean(StripeAdapter.class).hasSingleBean(StripeClient.class)
                    .hasSingleBean(StripePaymentMapper.class)
                    .hasBean("stripeWebClient").hasBean("paypalWebClient").hasBean("redsysWebClient");
            var props = context.getBean(WalletPaymentProviderProps.class);
            assertThat(props.getPaymentProviders()).containsOnlyKeys("stripe", "paypal", "redsys");
            assertThat(props.provider("stripe").getChargesUri()).hasToString("https://sandbox.playtomic.io/v1/stripe-simulator/charges");
            assertThat(props.provider("paypal").getOrdersUri()).hasToString("http://127.0.0.1:9999/v1/paypal-simulator/v2/checkout/orders");
            assertThat(props.provider("redsys").getAuthorizationUri()).hasToString("http://127.0.0.1:9999/v1/redsys-simulator/sis/rest/trataPeticion");
            assertThat(props.getPaymentProviders().values()).allSatisfy(provider -> {
                assertThat(provider.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                assertThat(provider.getRequestTimeout()).isEqualTo(Duration.ofSeconds(5));
            });
        });
    }

    @Test
    void listsOnlyEnabledAndApprovedTopUpAdaptersInTheLocalProfile() {
        var local = runner.withPropertyValues("spring.profiles.active=local-top-up");
        local.run(context -> assertThat(context.getBean(WalletTopUpProviders.class).activeIds()).containsExactly("stripe"));
        local.withPropertyValues("wallet.payment-providers.stripe.top-up-enabled=false")
                .run(context -> assertThat(context.getBean(WalletTopUpProviders.class).activeIds()).isEmpty());
        local.withPropertyValues("wallet.payment-providers.stripe.charges-uri=https://example.invalid/charges")
                .run(context -> assertThat(context.getBean(WalletTopUpProviders.class).activeIds()).isEmpty());
        local.withPropertyValues("wallet.payment-providers.stripe.charges-uri=http://127.0.0.1:9999/other")
                .run(context -> assertThat(context.getBean(WalletTopUpProviders.class).activeIds()).isEmpty());
    }

    @Test
    void bindsTestProfileAndPrefersProviderOverridesOverLegacyAliases() {
        runner.withPropertyValues("spring.profiles.active=test",
                "STRIPE_SIMULATOR_CHARGESURI=http://127.0.0.1:9998/legacy",
                "WALLET_PAYMENT_PROVIDERS_STRIPE_CHARGES_URI=http://127.0.0.1:9999/charges",
                "WALLET_SIMULATOR_PAYPAL_ORDERS_URI=http://127.0.0.1:9998/orders",
                "WALLET_PAYMENT_PROVIDERS_REDSYS_CONNECT_TIMEOUT=3s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var props = context.getBean(WalletPaymentProviderProps.class);
                    assertThat(props.provider("stripe").getChargesUri()).hasToString("http://127.0.0.1:9999/charges");
                    assertThat(props.provider("stripe").getRefundsUri()).isEqualTo("http://localhost:9999/payments/{payment_id}/refunds");
                    assertThat(props.provider("paypal").getOrdersUri()).hasToString("http://127.0.0.1:9998/orders");
                    assertThat(props.provider("paypal").getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(props.provider("redsys").getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
                });
    }

    @Test
    void rejectsInvalidProviderEndpointAndTimeout() {
        runner.withPropertyValues("wallet.payment-providers.paypal.orders-uri=ftp://localhost/orders")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("wallet.payment-providers.redsys.request-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void createsAndCapturesPayPalOrderAndAuthorizesRedsysOverHttp() {
        var bodies = new ConcurrentLinkedQueue<String>();
        var server = HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes
                .post("/orders", (request, response) -> request.receive().aggregate().asString()
                        .flatMap(body -> { bodies.add(body); return respond(response, 201, CREATED); }))
                .post("/orders/order-1/capture", (request, response) -> request.receive().aggregate().asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> { bodies.add(body); return respond(response, 201, CAPTURED); }))
                .post("/redsys", (request, response) -> request.receive().aggregate().asString()
                        .flatMap(body -> { bodies.add(body); return respond(response, 200, redsys("0000", "1525")); })))
                .bindNow(DEADLINE);
        try {
            configured(server.port()).run(context -> {
                var paypal = context.getBean(PayPalAdapter.class);
                var redsys = context.getBean(RedsysAdapter.class);
                StepVerifier.create(paypal.createOrder(AMOUNT).flatMap(paypal::captureOrder))
                        .expectNext(ProviderPaymentResult.approved("capture-1")).expectComplete().verify(DEADLINE);
                StepVerifier.create(redsys.authorize(ORDER, AMOUNT))
                        .expectNext(ProviderPaymentResult.approved("abcdef")).expectComplete().verify(DEADLINE);
            });
            assertThat(json.readTree(bodies.remove())).isEqualTo(json.readTree("""
                    {"intent":"CAPTURE","purchase_units":[{"amount":{"currency_code":"EUR","value":"15.25"}}]}
                    """));
            assertThat(bodies.remove()).isEmpty();
            var envelope = json.readTree(bodies.remove());
            assertThat(envelope.path("Ds_SignatureVersion").asString()).isEqualTo("SIMULATOR_ONLY");
            assertThat(envelope.path("Ds_Signature").asString()).isEqualTo("SIMULATOR_UNSIGNED");
            assertThat(json.readTree(Base64.getUrlDecoder().decode(envelope.path("Ds_MerchantParameters").asString())))
                    .isEqualTo(json.readTree("""
                            {"DS_MERCHANT_ORDER":"1234AB","DS_MERCHANT_AMOUNT":"1525",
                             "DS_MERCHANT_CURRENCY":"978","DS_MERCHANT_TRANSACTIONTYPE":"0"}
                            """));
            assertThat(bodies).isEmpty();
        } finally {
            server.disposeNow(DEADLINE);
        }
    }

    @Test
    void separatesConfirmedDeclinesFromDuplicateCaptureAndMismatchedAuthorization() {
        var captures = new AtomicInteger();
        var authorizations = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes
                .post("/orders", (request, response) -> respond(response, 201, CREATED))
                .post("/orders/order-1/capture", (request, response) -> respond(response, 422, """
                        {"name":"UNPROCESSABLE_ENTITY","details":[{"issue":"%s"}]}
                        """.formatted(captures.getAndIncrement() == 0 ? "INSTRUMENT_DECLINED" : "ORDER_ALREADY_CAPTURED")))
                .post("/redsys", (request, response) -> respond(response, 200,
                        authorizations.getAndIncrement() == 0 ? redsys("0101", "1525") : redsys("0000", "1526"))))
                .bindNow(DEADLINE);
        try {
            configured(server.port()).run(context -> {
                var paypal = context.getBean(PayPalAdapter.class);
                var redsys = context.getBean(RedsysAdapter.class);
                var capture = paypal.createOrder(AMOUNT).flatMap(paypal::captureOrder);
                StepVerifier.create(capture).expectNext(ProviderPaymentResult.declined()).expectComplete().verify(DEADLINE);
                StepVerifier.create(capture).expectErrorSatisfies(error -> assertFailure(error,
                        CommonErrorCode.ILLEGAL_STATE_EXCEPTION, IllegalStateException.class, "PayPal capture could not be confirmed")).verify(DEADLINE);
                StepVerifier.create(redsys.authorize(ORDER, AMOUNT))
                        .expectNext(ProviderPaymentResult.declined()).expectComplete().verify(DEADLINE);
                StepVerifier.create(redsys.authorize(ORDER, AMOUNT))
                        .expectErrorSatisfies(error -> assertFailure(error, CommonErrorCode.ILLEGAL_STATE_EXCEPTION,
                                IllegalStateException.class, "Redsys response does not match the request")).verify(DEADLINE);
            });
        } finally {
            server.disposeNow(DEADLINE);
        }
    }

    @Test
    void reportsTimeoutWithoutDispatchingAnotherAuthorization() {
        var requests = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes
                .post("/redsys", (request, response) -> {
                    requests.incrementAndGet();
                    return Mono.never();
                })).bindNow(DEADLINE);
        try {
            configured(server.port()).withPropertyValues("WALLET_PAYMENT_PROVIDERS_REDSYS_REQUEST_TIMEOUT=1s").run(context -> {
                var redsys = context.getBean(RedsysAdapter.class);
                StepVerifier.create(redsys.authorize(ORDER, AMOUNT))
                        .expectErrorSatisfies(error -> assertFailure(error, CommonErrorCode.TIME_BOX_EXCEPTION,
                                java.util.concurrent.TimeoutException.class, "Payment provider response could not be confirmed")).verify(DEADLINE);
            });
            assertThat(requests).hasValue(1);
        } finally {
            server.disposeNow(DEADLINE);
        }
    }

    private static void assertFailure(Throwable error, CommonErrorCode code, Class<? extends Throwable> cause, String detail) {
        assertThat(error).isInstanceOfSatisfying(BusinessException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(code);
            assertThat(failure.getCause()).isInstanceOf(cause);
            assertThat(failure.getAdditionalInfo()).contains(detail);
        });
    }

    private ApplicationContextRunner configured(int port) {
        return runner.withPropertyValues("WALLET_PAYMENT_PROVIDERS_PAYPAL_ORDERS_URI=http://127.0.0.1:" + port + "/orders",
                "WALLET_PAYMENT_PROVIDERS_REDSYS_AUTHORIZATION_URI=http://127.0.0.1:" + port + "/redsys");
    }

    private String redsys(String code, String amount) {
        var response = new java.util.HashMap<>(Map.of("Ds_Order", ORDER, "Ds_Amount", amount, "Ds_Currency", "978",
                "Ds_TransactionType", "0", "Ds_Response", code));
        if ("0000".equals(code)) {
            response.put("Ds_AuthorisationCode", "abcdef");
        }
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(response));
        return json.writeValueAsString(Map.of("Ds_MerchantParameters", encoded,
                "Ds_SignatureVersion", "SIMULATOR_ONLY", "Ds_Signature", "SIMULATOR_UNSIGNED"));
    }

    private static Mono<Void> respond(HttpServerResponse response, int status, String body) {
        return response.status(status).header("Content-Type", "application/json").sendString(Mono.just(body)).then();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({CommonProps.class, WalletPaymentProviderProps.class})
    @ComponentScan(basePackageClasses = {PayPalClient.class, StripePaymentMapper.class})
    static class ClientProperties {
    }
}