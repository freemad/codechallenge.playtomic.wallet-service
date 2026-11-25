package com.playtomic.tests.wallet.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

@ConfigurationProperties(WalletReconciliationProps.PREFIX)
public record WalletReconciliationProps(Duration interval, Duration lease, Duration runTimeout,
                                        Duration stalePendingAge, Duration receiptDelay, int batchSize) {
    public static final String PREFIX = "wallet.reconciliation";

    public WalletReconciliationProps {
        Assert.isTrue(interval != null && interval.isPositive(), PREFIX + ".interval must be positive");
        Assert.isTrue(runTimeout != null && runTimeout.isPositive(), PREFIX + ".run-timeout must be positive");
        Assert.isTrue(lease != null && lease.compareTo(runTimeout) > 0,
                PREFIX + ".lease must be longer than run-timeout");
        Assert.isTrue(stalePendingAge != null && stalePendingAge.isPositive(),
                PREFIX + ".stale-pending-age must be positive");
        Assert.isTrue(receiptDelay != null && receiptDelay.isPositive(), PREFIX + ".receipt-delay must be positive");
        Assert.isTrue(batchSize > 0, PREFIX + ".batch-size must be positive");
    }
}

