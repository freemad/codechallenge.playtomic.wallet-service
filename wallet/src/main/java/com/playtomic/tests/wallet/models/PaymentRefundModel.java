package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.Value;

import java.io.Serial;

/** Identifies the provider payment to refund; this is not a wallet purchase return. */
@Value
public class PaymentRefundModel implements IModel {
    @Serial
    private static final long serialVersionUID = -593421011441436549L;

    String paymentId;

    /** Identifies the original payment to refund. */
    public PaymentRefundModel(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Provider payment ID is required");
        }
        this.paymentId = paymentId;
    }
}

