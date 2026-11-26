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

/** Tracks a wallet's next audit cutoff, opening checkpoint and worker claim. */
@Value
@RequiredArgsConstructor
@Document("wallet_audit_schedule")
public class WalletAuditScheduleEntity implements IEntity<String> {
    @Serial
    private static final long serialVersionUID = 1L;

    @MongoId String id;
    String currency;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal openingBalance;
    long openingSequence;
    Instant nextCutoff;
    String token;
    boolean blocked;
    Instant availableAt;

    @Transient
    @NonFinal
    @Setter
    @Accessors(chain = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    boolean newRecord;
}