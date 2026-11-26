package com.playtomic.tests.wallet.entities;

import lombok.Value;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;
import java.math.BigDecimal;

/** Insert-only; the primary key makes each wallet/sequence unique even before index initialization. */
@Value
@Document("wallet_ledger")
public class WalletPostingEntity {
    @MongoId String id;
    String walletId;
    long sequence;
    String currency;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal delta;
}
