package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.utils.EnumLookup;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WalletEventType {
    WALLET_DEBITED(2, "WalletDebited"),
    WALLET_CREDITED(1, "WalletCredited");

    private final int value;
    private final String wireValue;

    public static WalletEventType ofValue(int value) {
        return EnumLookup.find(values(), type -> type.value == value, "Unknown wallet event type code: " + value);
    }

    public static WalletEventType fromWireValue(String wireValue) {
        for (WalletEventType eventType : values()) {
            if (eventType.wireValue.equals(wireValue)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("Unknown wallet event type: " + wireValue);
    }
}