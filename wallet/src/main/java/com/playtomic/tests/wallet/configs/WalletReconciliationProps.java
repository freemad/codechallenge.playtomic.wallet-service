package com.playtomic.tests.wallet.configs;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

@Value
@ConfigurationProperties(WalletReconciliationProps.PREFIX)
public class WalletReconciliationProps {
    public static final String PREFIX = "wallet.reconciliation";
    Duration interval;
    Duration lease;
    Duration runTimeout;
    Duration stalePendingAge;
    Duration receiptDelay;
    int batchSize;

    public WalletReconciliationProps(Duration interval, Duration lease, Duration runTimeout, Duration stalePendingAge,
                                     Duration receiptDelay, int batchSize) {
        Assert.isTrue(interval != null && interval.isPositive(), PREFIX + ".interval must be positive");
        Assert.isTrue(runTimeout != null && runTimeout.isPositive(), PREFIX + ".run-timeout must be positive");
        Assert.isTrue(lease != null && lease.compareTo(runTimeout) > 0,
                PREFIX + ".lease must be longer than run-timeout");
        Assert.isTrue(stalePendingAge != null && stalePendingAge.isPositive(),
                PREFIX + ".stale-pending-age must be positive");
        Assert.isTrue(receiptDelay != null && receiptDelay.isPositive(), PREFIX + ".receipt-delay must be positive");
        Assert.isTrue(batchSize > 0, PREFIX + ".batch-size must be positive");

        this.interval = interval;
        this.lease = lease;
        this.runTimeout = runTimeout;
        this.stalePendingAge = stalePendingAge;
        this.receiptDelay = receiptDelay;
        this.batchSize = batchSize;
    }
}
