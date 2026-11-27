package com.playtomic.tests.common.enums;

import com.playtomic.tests.common.contracts.ProviderTransactionState;

/** Order states emitted by the local PayPal fixture. */
public enum PayPalOrderState implements ProviderTransactionState {
    APPROVED("APPROVED"), COMPLETED("COMPLETED");

    private final String wireValue;

    PayPalOrderState(String wireValue) { this.wireValue = wireValue; }

    @Override
    public String wireValue() { return wireValue; }
}