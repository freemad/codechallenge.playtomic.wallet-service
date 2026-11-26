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
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.io.Serial;
import java.time.Instant;

/** Stores a provider receipt keyed by provider payment identifier. */
@Value
@Accessors(chain = true)
@RequiredArgsConstructor
@Document("wallet_payment_receipts")
public class WalletPaymentReceiptEntity implements IEntity<String> {
    @Serial
    private static final long serialVersionUID = 8841442591861714084L;

    @MongoId
    String providerPaymentId;
    String operationId;
    Instant recordedAt;

    @Transient
    @NonFinal
    @Setter
    @Accessors(chain = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    boolean newRecord;

    /** Uses the provider payment identifier as the receipt document key. */
    @Override
    public String getId() {
        return providerPaymentId;
    }
}