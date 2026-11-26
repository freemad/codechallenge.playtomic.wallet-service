package com.playtomic.tests.wallet.configs;

import com.playtomic.tests.wallet.controllers.WalletControllerV1;
import com.playtomic.tests.wallet.mappers.WalletResponseMapperV1;
import com.playtomic.tests.wallet.mappers.WalletTopUpResponseMapperV1;
import com.playtomic.tests.wallet.services.WalletLookupService;
import com.playtomic.tests.wallet.services.WalletMutationService;
import com.playtomic.tests.wallet.services.WalletTopUpProviders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class WalletTopUpHttpConfigurationTest {
    private final ReactiveWebApplicationContextRunner webRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(WalletTopUpHttpConfiguration.class, WalletControllerV1.class, WebConfig.class)
            .withBean(WalletLookupService.class, () -> mock(WalletLookupService.class))
            .withBean(WalletResponseMapperV1.class, () -> mock(WalletResponseMapperV1.class))
            .withBean(WalletMutationService.class, () -> mock(WalletMutationService.class))
            .withBean(WalletTopUpResponseMapperV1.class, () -> mock(WalletTopUpResponseMapperV1.class))
            .withBean(WalletTopUpProviders.class, () -> new WalletTopUpProviders(Map.of()));

    @Test
    void doesNotRegisterTheNewEndpointsByDefault() {
        webRunner.run(context -> {
            assertThat(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().values())
                    .extracting(handler -> handler.getMethod().getName()).containsExactlyInAnyOrder("log", "getWallet");
            var client = WebTestClient.bindToApplicationContext(context).build();
            client.get().uri("/api/v1/wallets/payment-providers").exchange().expectStatus().isNotFound();
            client.post().uri("/api/v1/wallets/wallet-1/top-ups").exchange().expectStatus().isNotFound();
            verifyNoInteractions(context.getBean(WalletLookupService.class), context.getBean(WalletMutationService.class));
        });
    }

    @Test
    void registersBothLocalRoutesOnTheWalletController() {
        localRunner().run(context -> {
            var handlers = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().values();
            assertThat(handlers).extracting(HandlerMethod::getBeanType).containsOnly(WalletControllerV1.class);
            assertThat(handlers).extracting(handler -> handler.getMethod().getName())
                    .containsExactlyInAnyOrder("log", "getWallet", "getPaymentProviders", "topUp");
            var client = WebTestClient.bindToApplicationContext(context).build();
            client.get().uri("/api/v1/wallets/payment-providers").accept(MediaType.APPLICATION_JSON).exchange()
                    .expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                    .expectBody().jsonPath("$.result.providers").isArray();
            client.get().uri("/api/v1/wallets/payment-providers").accept(MediaType.TEXT_PLAIN).exchange()
                    .expectStatus().isEqualTo(406);
            client.post().uri("/api/v1/wallets/wallet-1/top-ups").contentType(MediaType.TEXT_PLAIN)
                    .bodyValue("unsupported").exchange().expectStatus().isEqualTo(415);
            client.get().uri("/wallets/payment-providers").exchange().expectStatus().isNotFound();
            client.post().uri("/wallets/wallet-1/top-ups").exchange().expectStatus().isNotFound();
            verifyNoInteractions(context.getBean(WalletLookupService.class), context.getBean(WalletMutationService.class));
        });
    }

    @Test
    void rejectsAListenerExposedOutsideLoopback() {
        localRunner().withPropertyValues("server.address=0.0.0.0")
                .run(context -> assertThat(context).getFailure().hasRootCauseMessage(
                        "local-top-up requires server.address=127.0.0.1"));
    }

    @Test
    void rejectsEnablingUnauthenticatedTopUpOutsideTheApprovedProfile() {
        webRunner.withPropertyValues("wallet.top-up.http.enabled=true", "server.address=127.0.0.1",
                        "wallet.top-up.http.synthetic-card-number=4242424242424242")
                .run(context -> assertThat(context).getFailure().hasRootCauseMessage(
                        "Unauthenticated top-up HTTP requires the local-top-up profile"));
    }

    @Test
    void rejectsReplacingTheApprovedSyntheticCard() {
        localRunner().withPropertyValues("wallet.top-up.http.synthetic-card-number=not-approved")
                .run(context -> assertThat(context).getFailure().hasRootCauseMessage(
                        "wallet.top-up.http.synthetic-card-number must be the documented synthetic card when enabled"));
    }

    @Test
    void canDisableBothRoutesEvenWithTheProfileActive() {
        localRunner().withPropertyValues("wallet.top-up.http.enabled=false").run(context ->
                assertThat(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().values())
                        .extracting(handler -> handler.getMethod().getName()).containsExactlyInAnyOrder("log", "getWallet"));
    }

    @Test
    void reservesBothLiteralSegmentsRegardlessOfProfile() {
        for (var runner : java.util.List.of(webRunner, localRunner())) {
            runner.run(context -> {
                var client = WebTestClient.bindToApplicationContext(context).build();
                client.get().uri("/api/v1/wallets/log").exchange().expectStatus().isOk();
                for (String name : java.util.List.of("log", "payment-providers")) {
                    client.post().uri("/api/v1/wallets/" + name + "/top-ups").exchange().expectStatus().isNotFound();
                }
                verifyNoInteractions(context.getBean(WalletLookupService.class), context.getBean(WalletMutationService.class));
            });
        }
    }

    private ReactiveWebApplicationContextRunner localRunner() {
        return webRunner.withPropertyValues("spring.profiles.active=local-top-up");
    }

    @EnableConfigurationProperties(WalletTopUpHttpProps.class)
    static class WebConfig {
    }
}