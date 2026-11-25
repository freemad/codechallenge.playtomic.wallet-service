package com.playtomic.tests.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.constants.CommonParam;
import com.playtomic.tests.common.utils.EnumLookup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PaymentExecutionStatus {
    PAYMENT_EXECUTION_STATUS_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    PAYMENT_EXECUTION_STATUS_UNKNOWN((short) 1, "unknown"),
    PAYMENT_EXECUTION_STATUS_HELD((short) 2, "held"),
    PAYMENT_EXECUTION_STATUS_AUTHORIZED((short) 3, "authorized"),
    PAYMENT_EXECUTION_STATUS_REJECTED((short) 4, "rejected"),
    PAYMENT_EXECUTION_STATUS_SETTLED((short) 5, "settled"),
    PAYMENT_EXECUTION_STATUS_FAILED((short) 6, "failed"),
    PAYMENT_EXECUTION_STATUS_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    @JsonIgnore
    public boolean isTerminal() {
        return this == PAYMENT_EXECUTION_STATUS_REJECTED || this == PAYMENT_EXECUTION_STATUS_SETTLED
                || this == PAYMENT_EXECUTION_STATUS_FAILED;
    }

    public static PaymentExecutionStatus validate(PaymentExecutionStatus candidate) {
        return EnumLookup.requireOperational(candidate, PAYMENT_EXECUTION_STATUS_UNSPECIFIED,
                PAYMENT_EXECUTION_STATUS_ERROR, "Payment execution status requires an operational value");
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static PaymentExecutionStatus ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), status -> status.value == value, "Unknown PaymentExecutionStatus value: " + value);
    }

    public static PaymentExecutionStatus ofName(String name) {
        return EnumLookup.find(values(), status -> status.name.equals(name), "Unknown PaymentExecutionStatus name: " + name);
    }
}
