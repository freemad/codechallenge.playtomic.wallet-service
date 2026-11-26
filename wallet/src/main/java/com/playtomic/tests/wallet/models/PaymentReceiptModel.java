package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.Value;

import java.io.Serial;

/** Identifies a charge accepted by the payment provider. */
@Value
public class PaymentReceiptModel implements IModel {
    @Serial
    private static final long serialVersionUID = 6650069077198245769L;

    String id;

    /** Identifies the receipt returned for an accepted charge. */
    public PaymentReceiptModel(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Provider payment ID is required");
        }
        this.id = id;
    }
}

