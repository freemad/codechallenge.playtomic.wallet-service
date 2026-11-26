package com.playtomic.tests.wallet.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/** Controls exposure of the demo HTTP routes and the permitted synthetic card. */
@ConfigurationProperties("wallet.top-up.http")
public class WalletTopUpHttpProps {
    static final String DEMO_PROFILE = "local-top-up";
    static final String LOOPBACK_ADDRESS = "127.0.0.1";
    private static final String APPROVED_SYNTHETIC_CARD_NUMBER = "4242424242424242";

    private final boolean enabled;
    private final String syntheticCardNumber;

    /** Binds the settings for wallet top up http. */
    public WalletTopUpHttpProps(boolean enabled, String syntheticCardNumber) {
        Assert.isTrue((!enabled && syntheticCardNumber == null)
                        || APPROVED_SYNTHETIC_CARD_NUMBER.equals(syntheticCardNumber),
                "wallet.top-up.http.synthetic-card-number must be the documented synthetic card when enabled");
        this.enabled = enabled;
        this.syntheticCardNumber = syntheticCardNumber;
    }

    /** Checks the submitted number against the synthetic card allowed for the local demo. */
    public boolean acceptsCardNumber(String cardNumber) {
        return APPROVED_SYNTHETIC_CARD_NUMBER.equals(cardNumber) && cardNumber.equals(syntheticCardNumber);
    }

    boolean isEnabled() {
        return enabled;
    }

    static void requireApprovedExposure(Environment environment) {
        Assert.isTrue(environment.matchesProfiles(DEMO_PROFILE),
                "Unauthenticated top-up HTTP requires the " + DEMO_PROFILE + " profile");
        Assert.isTrue(LOOPBACK_ADDRESS.equals(environment.getProperty("server.address")),
                DEMO_PROFILE + " requires server.address=" + LOOPBACK_ADDRESS);
    }
}