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
public enum PaymentDeliveryStatus {
    PAYMENT_DELIVERY_STATUS_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    PAYMENT_DELIVERY_STATUS_PUBLISHED((short) 1, "published"),
    PAYMENT_DELIVERY_STATUS_ACCEPTED((short) 2, "accepted"),
    PAYMENT_DELIVERY_STATUS_APPLIED((short) 3, "applied"),
    PAYMENT_DELIVERY_STATUS_FAILED((short) 4, "failed"),
    PAYMENT_DELIVERY_STATUS_UNKNOWN((short) 5, "unknown"),
    PAYMENT_DELIVERY_STATUS_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    public static PaymentDeliveryStatus validate(PaymentDeliveryStatus candidate) {
        return EnumLookup.requireOperational(candidate, PAYMENT_DELIVERY_STATUS_UNSPECIFIED,
                PAYMENT_DELIVERY_STATUS_ERROR, "Payment delivery status requires an operational value");
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static PaymentDeliveryStatus ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), status -> status.value == value, "Unknown PaymentDeliveryStatus value: " + value);
    }

    public static PaymentDeliveryStatus ofName(String name) {
        return EnumLookup.find(values(), status -> status.name.equals(name), "Unknown PaymentDeliveryStatus name: " + name);
    }
}
