package com.playtomic.tests.wallet.entities;

import com.playtomic.tests.common.contracts.models.IEntity;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import com.playtomic.tests.wallet.enums.WalletOperationMongoType;
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

/** Stores the durable request identity, status and result of a wallet mutation. */
@Value
@Accessors(chain = true)
@RequiredArgsConstructor
@Document("wallet_operations")
public class WalletOperationEntity implements IEntity<String> {
    @Serial
    private static final long serialVersionUID = -5315448997993430311L;

    @MongoId
    String id;
    String walletId;
    WalletOperationMongoType type;
    @Field(targetType = FieldType.DECIMAL128)
    BigDecimal amount;
    String currency;
    WalletOperationMongoStatus status;
    @Field(targetType = FieldType.DECIMAL128)
    BigDecimal resultingBalance;
    Instant updatedAt;
    String provider;

    @Transient
    @NonFinal
    @Setter
    @Accessors(chain = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    boolean newRecord;
}
