package com.playtomic.tests.common.contracts;

import java.math.BigDecimal;

/** Amounts on the currency-less Stripe simulator wire are decimal EUR major units. */
public final class StripeSimulatorMoney {
    public static final String CURRENCY = "EUR";
    private static final BigDecimal MIN = new BigDecimal("0.01");
    private static final BigDecimal MAX = new BigDecimal("999999.99");

    private StripeSimulatorMoney() { }

    public static boolean accepts(BigDecimal amount) {
        return amount != null && amount.compareTo(MIN) >= 0 && amount.compareTo(MAX) <= 0
                && amount.stripTrailingZeros().scale() <= 2;
    }
}
