package com.playtomic.tests.wallet.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.constants.CommonParam;
import com.playtomic.tests.common.enums.EnumLookup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum WalletOperationType {
    WALLET_OPERATION_TYPE_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    WALLET_OPERATION_TYPE_TOP_UP((short) 1, "top_up"),
    WALLET_OPERATION_TYPE_SPEND((short) 2, "spend"),
    WALLET_OPERATION_TYPE_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    @JsonIgnore
    public String metricLabel() {
        return switch (validate(this)) {
            case WALLET_OPERATION_TYPE_TOP_UP -> "TOP_UP";
            case WALLET_OPERATION_TYPE_SPEND -> "SPEND";
            default -> throw new IllegalStateException("Non-operational wallet operation type");
        };
    }

    public static WalletOperationType validate(WalletOperationType candidate) {
        return EnumLookup.requireOperational(candidate, WALLET_OPERATION_TYPE_UNSPECIFIED,
                WALLET_OPERATION_TYPE_ERROR, "Wallet operation type requires an operational value");
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static WalletOperationType ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), type -> type.value == value, "Unknown WalletOperationType value: " + value);
    }

    public static WalletOperationType ofName(String name) {
        return EnumLookup.find(values(), type -> type.name.equals(name), "Unknown WalletOperationType name: " + name);
    }
}

