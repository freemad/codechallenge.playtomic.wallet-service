package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.utils.EnumLookup;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operation types persisted using their stable numeric codes. */
@Getter
@AllArgsConstructor
public enum WalletOperationMongoType {
    TOP_UP(1),
    SPEND(2);

    private final int value;

    public static WalletOperationMongoType ofValue(int value) {
        return EnumLookup.find(values(), type -> type.value == value, "Unknown wallet operation type code: " + value);
    }
}
