package com.playtomic.tests.common.enums;

import com.playtomic.tests.common.contracts.ProviderTransactionState;

/** Response codes on the unsigned Redsys fixture wire, including leading zeroes. */
public enum RedsysAuthorizationState implements ProviderTransactionState {
    APPROVED("0000"), DECLINED("0101");

    private final String wireValue;

    RedsysAuthorizationState(String wireValue) { this.wireValue = wireValue; }

    @Override
    public String wireValue() { return wireValue; }
}