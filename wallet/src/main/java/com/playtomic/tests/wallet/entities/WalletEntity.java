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

@Value
@Accessors(chain = true)
@RequiredArgsConstructor
@Document("wallets")
public class WalletEntity implements IEntity<String> {

    @Serial
    private static final long serialVersionUID = -4617452862152158089L;

    @MongoId
    String id;

    @Field(targetType = FieldType.DECIMAL128)
    BigDecimal balance;

    String currency;

    @NonFinal
    Long ledgerSequence;
    @NonFinal
    Long auditedLedgerSequence;
    @NonFinal
    @Field(targetType = FieldType.DECIMAL128)
    BigDecimal calculatedBalance;
    @NonFinal
    java.time.Instant calculationTimestamp;
    @NonFinal
    java.time.Instant cutoffAt;

    @Transient
    @NonFinal
    @Setter
    @Accessors(chain = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    boolean newRecord;
}