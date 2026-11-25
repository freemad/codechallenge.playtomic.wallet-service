package com.playtomic.tests.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.constants.CommonParam;
import com.playtomic.tests.common.utils.EnumLookup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PaymentReviewStatus {
    PAYMENT_REVIEW_STATUS_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    PAYMENT_REVIEW_STATUS_OPEN((short) 1, "open"),
    PAYMENT_REVIEW_STATUS_ESCALATED((short) 2, "escalated"),
    PAYMENT_REVIEW_STATUS_RESOLUTION_REQUESTED((short) 3, "resolution_requested"),
    PAYMENT_REVIEW_STATUS_CLOSED((short) 4, "closed"),
    PAYMENT_REVIEW_STATUS_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    public static PaymentReviewStatus validate(PaymentReviewStatus candidate) {
        return EnumLookup.requireOperational(candidate, PAYMENT_REVIEW_STATUS_UNSPECIFIED,
                PAYMENT_REVIEW_STATUS_ERROR, "Payment review status requires an operational value");
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static PaymentReviewStatus ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), status -> status.value == value, "Unknown PaymentReviewStatus value: " + value);
    }

    public static PaymentReviewStatus ofName(String name) {
        return EnumLookup.find(values(), status -> status.name.equals(name), "Unknown PaymentReviewStatus name: " + name);
    }
}