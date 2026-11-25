package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.Value;

import java.io.Serial;

@Value
public class PaymentRefundModel implements IModel {
    @Serial
    private static final long serialVersionUID = -593421011441436549L;

    String paymentId;

    public PaymentRefundModel(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Provider payment ID is required");
        }
        this.paymentId = paymentId;
    }
}

