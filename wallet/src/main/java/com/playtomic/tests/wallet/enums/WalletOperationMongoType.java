package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.constants.CommonParam;
import com.playtomic.tests.common.utils.EnumLookup;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operation types persisted using their stable numeric codes. */
@Getter
@AllArgsConstructor
public enum WalletOperationMongoType {
    UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    TOP_UP(1, "top_up"),
    SPEND(2, "spend"),
    ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final int value;
    /** Stable audit label, independent of the Java constant name. */
    private final String name;

    public static WalletOperationMongoType validate(WalletOperationMongoType candidate) {
        return EnumLookup.requireOperational(candidate, UNSPECIFIED, ERROR, "Wallet operation type requires an operational value");
    }

    public static WalletOperationMongoType ofName(String name) {
        return EnumLookup.find(values(), type -> type.name.equals(name), "Unknown wallet operation type name: " + name);
    }

    public static WalletOperationMongoType ofValue(int value) {
        return EnumLookup.find(values(), type -> type.value == value, "Unknown wallet operation type code: " + value);
    }
}
