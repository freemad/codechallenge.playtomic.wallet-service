package com.playtomic.tests.wallet.configs;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

import java.net.URI;
import java.time.Duration;

import static com.playtomic.tests.wallet.constants.StripeSimulatorParam.PAYMENT_ID_PLACEHOLDER;

@Getter
@Accessors(chain = true)
@ConfigurationProperties("stripe.simulator")
public final class StripeSimulatorProps {

    private final URI chargesUri;
    private final String refundsUri;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public StripeSimulatorProps(URI chargesUri, String refundsUri,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("5s") Duration requestTimeout) {
        Assert.isTrue(isHttpUri(chargesUri), "stripe.simulator.charges-uri must be an HTTP(S) URI");
        Assert.isTrue(refundsUri != null && refundsUri.contains(PAYMENT_ID_PLACEHOLDER)
                        && isHttpUri(URI.create(refundsUri.replace(PAYMENT_ID_PLACEHOLDER, "id"))),
                "stripe.simulator.refunds-uri must be an HTTP(S) URI template with " + PAYMENT_ID_PLACEHOLDER);
        Assert.isTrue(connectTimeout.compareTo(Duration.ofMillis(1)) >= 0
                        && connectTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0,
                "stripe.simulator.connect-timeout must fit in a positive integer number of milliseconds");
        Assert.isTrue(requestTimeout.isPositive(), "stripe.simulator.request-timeout must be positive");
        this.chargesUri = chargesUri;
        this.refundsUri = refundsUri;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
    }

    private static boolean isHttpUri(URI uri) {
        return uri != null && uri.getHost() != null
                && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
    }
}