package com.playtomic.tests.wallet.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.constants.CommonParam;
import com.playtomic.tests.common.utils.EnumLookup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Represents successful completion of work without a data payload. */
@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum WalletCompletion {
    WALLET_COMPLETION_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    WALLET_COMPLETION_COMPLETED((short) 1, "completed"),
    WALLET_COMPLETION_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    /** Returns the candidate only if it is an operational value rather than null or a sentinel. */
    public static WalletCompletion validate(WalletCompletion candidate) {
        return EnumLookup.requireOperational(candidate, WALLET_COMPLETION_UNSPECIFIED,
                WALLET_COMPLETION_ERROR, "Wallet completion requires an operational value");
    }

    /** Resolves the numeric representation to an enum member, rejecting unknown values. */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static WalletCompletion ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), completion -> completion.value == value, "Unknown WalletCompletion value: " + value);
    }

    /** Resolves the external name to an enum member, rejecting unknown names. */
    public static WalletCompletion ofName(String name) {
        return EnumLookup.find(values(), completion -> completion.name.equals(name), "Unknown WalletCompletion name: " + name);
    }
}

