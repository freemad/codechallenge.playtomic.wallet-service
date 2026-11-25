package com.playtomic.tests.wallet.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Wire names defined by the Stripe simulator. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StripeSimulatorParam {
    public static final String PAYMENT_ID_PLACEHOLDER = "{payment_id}";
    public static final String CREDIT_CARD_FIELD = "credit_card";
    public static final String AMOUNT_FIELD = "amount";
}

