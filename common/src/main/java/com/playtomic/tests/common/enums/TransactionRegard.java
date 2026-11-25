package com.playtomic.tests.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.constants.CommonParam;
import com.playtomic.tests.common.exceptions.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum TransactionRegard {
    TRANSACTION_REGARD_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    TRANSACTION_REGARD_LOAN((short) 1, "loan"),
    TRANSACTION_REGARD_PURCHASE((short) 2, "purchase"),
    TRANSACTION_REGARD_DEBTS((short) 3, "debts"),
    TRANSACTION_REGARD_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    /**
     * Rejects null and unspecified/error sentinels at required operational boundaries.
     * Parsing remains separate and permits known sentinels for storage and diagnostics.
     *
     * @param candidate value required by the operation
     * @return the validated value
     * @throws BusinessException if the value is not operationally valid
     */
    public static TransactionRegard validate(TransactionRegard candidate) {
        return EnumLookup.requireOperational(candidate, TRANSACTION_REGARD_UNSPECIFIED,
                TRANSACTION_REGARD_ERROR, "TransactionRegard requires a non-null operational value");
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static TransactionRegard ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), regard -> regard.value == value, "unknown TransactionRegard! value: " + value);
    }

    public static TransactionRegard ofName(String name) {
        return EnumLookup.find(values(), regard -> regard.name.equals(name), "unknown TransactionRegard! name: " + name);
    }

}