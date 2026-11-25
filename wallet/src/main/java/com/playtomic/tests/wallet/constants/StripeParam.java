package com.playtomic.tests.wallet.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Wire names used by the Stripe client. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StripeParam {
    public static final String PAYMENT_ID_PLACEHOLDER = "{payment_id}";
    public static final String CREDIT_CARD_FIELD = "credit_card";
    public static final String AMOUNT_FIELD = "amount";
}