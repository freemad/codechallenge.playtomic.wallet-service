package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.EnumLookup;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operation types persisted using their stable numeric codes. */
@Getter
@AllArgsConstructor
public enum WalletOperationMongoType {
    TOP_UP(1, "top_up"),
    SPEND(2, "spend");

    private final int value;
    /** Stable audit label, independent of the Java constant name. */
    private final String name;

    public static WalletOperationMongoType validate(WalletOperationMongoType candidate) {
        if (candidate == null) {
            throw new BusinessException(CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, "Wallet operation type requires an operational value");
        }
        return candidate;
    }

    public static WalletOperationMongoType ofName(String name) {
        return EnumLookup.find(values(), type -> type.name.equals(name), "Unknown wallet operation type name: " + name);
    }

    public static WalletOperationMongoType ofValue(int value) {
        return EnumLookup.find(values(), type -> type.value == value, "Unknown wallet operation type code: " + value);
    }
}
