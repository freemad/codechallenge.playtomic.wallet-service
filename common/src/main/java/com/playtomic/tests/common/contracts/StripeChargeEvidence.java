package com.playtomic.tests.common.contracts;

import com.playtomic.tests.common.enums.StripeChargeStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Terminal evidence returned by the local simulator's read-only reference lookup. */
public record StripeChargeEvidence(UUID reference, StripeChargeStatus status, String receiptId, BigDecimal amount, String currency) {
    public static final String REFERENCE_HEADER = "X-Charge-Reference";

    public StripeChargeEvidence {
        if (!StripeSimulatorMoney.accepts(amount) || !StripeSimulatorMoney.CURRENCY.equals(currency)
                || reference == null || status == null || (status == StripeChargeStatus.ACCEPTED
                ? receiptId == null || receiptId.isBlank() : receiptId != null)) {
            throw new IllegalArgumentException("Invalid charge evidence");
        }
    }
}
