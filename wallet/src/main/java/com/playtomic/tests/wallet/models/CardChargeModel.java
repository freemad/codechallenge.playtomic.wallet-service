package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.ToString;
import lombok.Value;

import java.io.Serial;
import java.math.BigDecimal;

/** Carries the card number and amount required for a provider charge. */
@Value
public class CardChargeModel implements IModel {
    @Serial
    private static final long serialVersionUID = 5736031323882942410L;

    @ToString.Exclude
    String creditCardNumber;
    BigDecimal amount;

    /** Pairs the supplied card number with the amount to charge. */
    public CardChargeModel(String creditCardNumber, BigDecimal amount) {
        if (creditCardNumber == null || creditCardNumber.isBlank() || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A card number and positive charge amount are required");
        }
        this.creditCardNumber = creditCardNumber;
        this.amount = amount;
    }
}

