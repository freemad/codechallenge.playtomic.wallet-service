package com.playtomic.tests.wallet.configs;

import com.playtomic.tests.common.configs.CommonProps;
import com.playtomic.tests.common.http.NonRetryingWebClientFactory;
import com.playtomic.tests.common.resilience.ReactiveCircuitBreaker;
import com.playtomic.tests.wallet.http.stripe.StripeAmountTooSmallException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class WalletPaymentConfiguration {
    private static final String PAYMENT_CIRCUIT_BREAKER = "wallet-payment";

    @Bean
    WebClient stripeSimulatorWebClient(NonRetryingWebClientFactory factory, StripeSimulatorProps settings) {
        return factory.create(settings.getConnectTimeout());
    }

    @Bean
    ReactiveCircuitBreaker walletPaymentCircuitBreaker(CommonProps common) {
        var settings = common.getResilience().getCircuitBreaker();
        var config = CircuitBreakerConfig.custom()
                .slidingWindowSize(settings.getSlidingWindowSize())
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
                .failureRateThreshold(settings.getFailureRateThreshold())
                .waitDurationInOpenState(Duration.ofMillis(settings.getOpenStateWaitMs()))
                .ignoreExceptions(StripeAmountTooSmallException.class)
                .build();
        return new ReactiveCircuitBreaker(CircuitBreaker.of(PAYMENT_CIRCUIT_BREAKER, config));
    }
}