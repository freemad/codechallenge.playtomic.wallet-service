package com.playtomic.tests.wallet.configs;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static com.playtomic.tests.wallet.constants.StripeParam.PAYMENT_ID_PLACEHOLDER;

@Getter
@ConfigurationProperties("wallet")
public final class WalletPaymentProviderProps {
    public static final String STRIPE = "stripe";
    public static final String PAYPAL = "paypal";
    public static final String REDSYS = "redsys";

    private final Map<String, Provider> paymentProviders;

    public WalletPaymentProviderProps(Map<String, Provider> paymentProviders) {
        this.paymentProviders = paymentProviders == null ? Map.of() : Map.copyOf(paymentProviders);
        var stripe = provider(STRIPE);
        Assert.isTrue(isHttpUri(stripe.getChargesUri()), "wallet.payment-providers.stripe.charges-uri must be an HTTP(S) URI");
        var refundsUri = stripe.getRefundsUri();
        Assert.isTrue(refundsUri != null && refundsUri.contains(PAYMENT_ID_PLACEHOLDER)
                        && isHttpUri(URI.create(refundsUri.replace(PAYMENT_ID_PLACEHOLDER, "id"))),
                "wallet.payment-providers.stripe.refunds-uri must be an HTTP(S) URI template with " + PAYMENT_ID_PLACEHOLDER);
        validateEndpoint(provider(PAYPAL).getOrdersUri(), "paypal.orders-uri");
        validateEndpoint(provider(REDSYS).getAuthorizationUri(), "redsys.authorization-uri");
    }

    public Provider provider(String name) {
        var settings = paymentProviders.get(name);
        Assert.notNull(settings, "Missing configuration for wallet.payment-providers." + name);
        return settings;
    }

    @Getter
    public static final class Provider {
        private final URI chargesUri;
        private final String refundsUri;
        private final URI ordersUri;
        private final URI authorizationUri;
        private final Duration connectTimeout;
        private final Duration requestTimeout;
        private final boolean topUpEnabled;
        private final URI localTopUpAllowedChargeUri;

        public Provider(URI chargesUri, String refundsUri, URI ordersUri, URI authorizationUri,
                        @DefaultValue("2s") Duration connectTimeout,
                        @DefaultValue("5s") Duration requestTimeout,
                        @DefaultValue("true") boolean topUpEnabled, URI localTopUpAllowedChargeUri) {
            Assert.isTrue(connectTimeout.compareTo(Duration.ofMillis(1)) >= 0
                            && connectTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0,
                    "connect-timeout must fit in a positive integer number of milliseconds");
            Assert.isTrue(requestTimeout.isPositive(), "request-timeout must be positive");
            this.chargesUri = chargesUri;
            this.refundsUri = refundsUri;
            this.ordersUri = ordersUri;
            this.authorizationUri = authorizationUri;
            this.connectTimeout = connectTimeout;
            this.requestTimeout = requestTimeout;
            this.topUpEnabled = topUpEnabled;
            this.localTopUpAllowedChargeUri = localTopUpAllowedChargeUri;
        }
    }

    private static boolean isHttpUri(URI uri) {
        return uri != null && uri.getHost() != null
                && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
    }

    private static void validateEndpoint(URI uri, String property) {
        Assert.isTrue(isHttpUri(uri) && uri.getUserInfo() == null && uri.getRawQuery() == null
                        && uri.getRawFragment() == null,
                "wallet.payment-providers." + property + " must be an HTTP(S) endpoint without credentials, query or fragment");
    }
}