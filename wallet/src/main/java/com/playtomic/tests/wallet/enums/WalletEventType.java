package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.utils.EnumLookup;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Identifies the kind of wallet event stored or published. */
@Getter
@AllArgsConstructor
public enum WalletEventType {
    WALLET_DEBITED(2, "WalletDebited"),
    WALLET_CREDITED(1, "WalletCredited");

    private final int value;
    private final String wireValue;

    /** Resolves the numeric representation to an enum member, rejecting unknown values. */
    public static WalletEventType ofValue(int value) {
        return EnumLookup.find(values(), type -> type.value == value, "Unknown wallet event type code: " + value);
    }

    /** Resolves the event identifier used on the wire. */
    public static WalletEventType fromWireValue(String wireValue) {
        for (WalletEventType eventType : values()) {
            if (eventType.wireValue.equals(wireValue)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("Unknown wallet event type: " + wireValue);
    }
}