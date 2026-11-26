package com.playtomic.tests.wallet.configs;

import com.playtomic.tests.common.configs.CommonProps;
import com.playtomic.tests.common.http.NonRetryingWebClientFactory;
import com.playtomic.tests.common.resilience.ReactiveCircuitBreaker;
import com.playtomic.tests.wallet.http.stripe.StripeAmountTooSmallException;
import com.playtomic.tests.wallet.services.WalletTopUpProviders;
import com.playtomic.tests.wallet.services.adapter.StripeAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

import static com.playtomic.tests.wallet.configs.WalletPaymentProviderProps.STRIPE;
import static com.playtomic.tests.wallet.configs.WalletTopUpHttpProps.DEMO_PROFILE;
import static com.playtomic.tests.wallet.configs.WalletTopUpHttpProps.LOOPBACK_ADDRESS;

@Configuration(proxyBeanMethods = false)
class WalletPaymentConfiguration {
    private static final String PAYMENT_CIRCUIT_BREAKER = "wallet-payment";

    @Bean
    WebClient stripeWebClient(NonRetryingWebClientFactory factory, WalletPaymentProviderProps settings) {
        return factory.create(settings.provider(WalletPaymentProviderProps.STRIPE).getConnectTimeout());
    }

    @Bean
    WebClient paypalWebClient(NonRetryingWebClientFactory factory, WalletPaymentProviderProps settings) {
        return factory.create(settings.provider(WalletPaymentProviderProps.PAYPAL).getConnectTimeout());
    }

    @Bean
    WebClient redsysWebClient(NonRetryingWebClientFactory factory, WalletPaymentProviderProps settings) {
        return factory.create(settings.provider(WalletPaymentProviderProps.REDSYS).getConnectTimeout());
    }

    @Bean
    WalletTopUpProviders walletTopUpProviders(WalletPaymentProviderProps settings, StripeAdapter stripe,
                                              Environment environment) {
        var provider = settings.provider(STRIPE);
        var chargeUri = provider.getChargesUri();
        boolean local = environment.matchesProfiles(DEMO_PROFILE);
        boolean approvedLocalTarget = LOOPBACK_ADDRESS.equals(chargeUri.getHost())
                && chargeUri.getUserInfo() == null && chargeUri.getRawQuery() == null && chargeUri.getRawFragment() == null
                && chargeUri.equals(provider.getLocalTopUpAllowedChargeUri());
        if (!provider.isTopUpEnabled() || (local && !approvedLocalTarget)) {
            return new WalletTopUpProviders(Map.of());
        }
        return new WalletTopUpProviders(Map.of(STRIPE, stripe::charge));
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