package com.playtomic.tests.wallet.configs;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

@Value
@ConfigurationProperties(WalletOutboxProps.PREFIX)
public class WalletOutboxProps {
    public static final String PREFIX = "wallet.outbox";

    Duration interval;
    String topic;
    Duration claimLease;
    Duration sendTimeout;
    int batchSize;

    public WalletOutboxProps(Duration interval, String topic, Duration claimLease, Duration sendTimeout, int batchSize) {
        Assert.isTrue(interval != null && interval.isPositive(), PREFIX + ".interval must be positive");
        Assert.hasText(topic, PREFIX + ".topic must not be blank");
        Assert.isTrue(sendTimeout != null && sendTimeout.isPositive(), PREFIX + ".send-timeout must be positive");
        Assert.isTrue(claimLease != null && claimLease.compareTo(sendTimeout) > 0,
                PREFIX + ".claim-lease must be longer than send-timeout to allow acknowledgment");
        Assert.isTrue(batchSize > 0, PREFIX + ".batch-size must be positive");
        this.interval = interval;
        this.topic = topic;
        this.claimLease = claimLease;
        this.sendTimeout = sendTimeout;
        this.batchSize = batchSize;
    }
}
