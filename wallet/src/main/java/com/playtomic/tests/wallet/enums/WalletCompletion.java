package com.playtomic.tests.wallet.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.constants.CommonParam;
import com.playtomic.tests.common.enums.EnumLookup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum WalletCompletion {
    WALLET_COMPLETION_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    WALLET_COMPLETION_COMPLETED((short) 1, "completed"),
    WALLET_COMPLETION_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    public static WalletCompletion validate(WalletCompletion candidate) {
        return EnumLookup.requireOperational(candidate, WALLET_COMPLETION_UNSPECIFIED,
                WALLET_COMPLETION_ERROR, "Wallet completion requires an operational value");
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static WalletCompletion ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), completion -> completion.value == value, "Unknown WalletCompletion value: " + value);
    }

    public static WalletCompletion ofName(String name) {
        return EnumLookup.find(values(), completion -> completion.name.equals(name), "Unknown WalletCompletion name: " + name);
    }
}

