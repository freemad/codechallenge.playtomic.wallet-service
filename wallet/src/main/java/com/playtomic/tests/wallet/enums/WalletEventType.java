package com.playtomic.tests.wallet.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WalletEventType {
    WALLET_DEBITED("WalletDebited"),
    WALLET_CREDITED("WalletCredited");

    private final String wireValue;

    public static WalletEventType fromWireValue(String wireValue) {
        for (WalletEventType eventType : values()) {
            if (eventType.wireValue.equals(wireValue)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("Unknown wallet event type: " + wireValue);
    }
}

