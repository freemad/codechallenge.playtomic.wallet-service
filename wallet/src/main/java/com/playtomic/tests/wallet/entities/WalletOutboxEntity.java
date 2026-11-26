package com.playtomic.tests.wallet.entities;

import com.playtomic.tests.common.contracts.models.IEntity;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;

/** Stores a wallet event and the lease and acknowledgment state for its publication. */
@Value
@Accessors(chain = true)
@RequiredArgsConstructor
@Document("wallet_outbox")
public class WalletOutboxEntity implements IEntity<String> {
    @Serial
    private static final long serialVersionUID = -906661431045405062L;

    @MongoId
    String id;
    String walletId;
    int eventType;
    int schemaVersion;
    @Field(targetType = FieldType.DECIMAL128)
    BigDecimal amount;
    String currency;
    @Field(targetType = FieldType.DECIMAL128)
    BigDecimal resultingBalance;
    Instant occurredAt;
    Instant availableAt;
    String leaseToken;
    Instant publishedAt;

    @Transient
    @NonFinal
    @Setter
    @Accessors(chain = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    boolean newRecord;
}