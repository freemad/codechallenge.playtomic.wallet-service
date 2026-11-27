package com.playtomic.tests.common.enums;

import com.playtomic.tests.common.contracts.ProviderTransactionState;

/** Terminal decisions exposed by the local Stripe charge lookup. */
public enum StripeChargeStatus implements ProviderTransactionState {
    ACCEPTED("ACCEPTED"), DECLINED("DECLINED");

    private final String wireValue;

    StripeChargeStatus(String wireValue) { this.wireValue = wireValue; }

    @Override
    public String wireValue() { return wireValue; }
}

