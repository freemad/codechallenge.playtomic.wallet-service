package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import com.playtomic.tests.wallet.enums.WalletEventType;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;

/** Represents a balance event awaiting publication or acknowledgment. */
@Value
@Accessors(chain = true)
public class WalletOutboxModel implements IModel {
    @Serial
    private static final long serialVersionUID = -7579406112618594902L;

    String id;
    String walletId;
    WalletEventType eventType;
    int schemaVersion;
    BigDecimal amount;
    String currency;
    BigDecimal resultingBalance;
    Instant occurredAt;
    Instant availableAt;
    @ToString.Exclude
    String leaseToken;
    Instant publishedAt;
}