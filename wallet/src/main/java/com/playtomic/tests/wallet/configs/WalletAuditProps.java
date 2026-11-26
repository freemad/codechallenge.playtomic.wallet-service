package com.playtomic.tests.wallet.configs;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import java.time.Duration;

/** Defines lease, deadline, batch and alert topic settings for the cutoff audit. */
@Value
@ConfigurationProperties("wallet.audit")
public class WalletAuditProps {
    String topic;
    Duration lease;
    Duration timeout;
    int batchSize;

    /** Binds the settings for wallet audit. */
    public WalletAuditProps(String topic, Duration lease, Duration timeout, int batchSize) {
        Assert.hasText(topic, "wallet.audit.topic must not be blank");
        Assert.isTrue(timeout != null && timeout.isPositive() && lease != null && lease.compareTo(timeout) > 0,
                "wallet.audit.lease must exceed positive timeout");
        Assert.isTrue(batchSize > 0, "wallet.audit.batch-size must be positive");
        this.topic = topic;
        this.lease = lease;
        this.timeout = timeout;
        this.batchSize = batchSize;
    }
}
