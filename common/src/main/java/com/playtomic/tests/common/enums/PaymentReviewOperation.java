package com.playtomic.tests.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.constants.CommonParam;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PaymentReviewOperation {
    PAYMENT_REVIEW_OPERATION_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    PAYMENT_REVIEW_OPERATION_CLAIM((short) 1, "claim"),
    PAYMENT_REVIEW_OPERATION_UNCLAIM((short) 2, "unclaim"),
    PAYMENT_REVIEW_OPERATION_PROCEED((short) 3, "proceed"),
    PAYMENT_REVIEW_OPERATION_REJECT((short) 4, "reject"),
    PAYMENT_REVIEW_OPERATION_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    public static PaymentReviewOperation validate(PaymentReviewOperation candidate) {
        return EnumLookup.requireOperational(candidate, PAYMENT_REVIEW_OPERATION_UNSPECIFIED,
                PAYMENT_REVIEW_OPERATION_ERROR, "Payment review operation requires an operational value");
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static PaymentReviewOperation ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), operation -> operation.value == value, "Unknown PaymentReviewOperation value: " + value);
    }

    public static PaymentReviewOperation ofName(String name) {
        return EnumLookup.find(values(), operation -> operation.name.equals(name), "Unknown PaymentReviewOperation name: " + name);
    }
}