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
    Type type;
    @Field(targetType = FieldType.DECIMAL128)
    BigDecimal amount;
    String currency;
    Status status;
    @Field(targetType = FieldType.DECIMAL128)
    BigDecimal resultingBalance;
    Instant updatedAt;

    @Transient
    @NonFinal
    @Setter
    @Accessors(chain = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    boolean newRecord;

    public enum Type { TOP_UP, SPEND }

    public enum Status {
        PENDING, REVIEW_REQUIRED, RECEIPT_RECORDED, SUCCEEDED, INSUFFICIENT_FUNDS,
        PAYMENT_REJECTED, PROVIDER_UNAVAILABLE, WALLET_UNAVAILABLE
    }
}
