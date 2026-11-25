package com.playtomic.tests.wallet.configs;

import com.playtomic.tests.common.configs.CommonProps;
import com.playtomic.tests.common.resilience.ReactiveCircuitBreaker;
import com.playtomic.tests.wallet.http.stripe.StripeAmountTooSmallException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class WalletPaymentConfiguration {
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
        return new ReactiveCircuitBreaker(CircuitBreaker.of("wallet-payment", config));
    }
}