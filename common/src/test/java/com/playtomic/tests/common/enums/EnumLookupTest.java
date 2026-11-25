package com.playtomic.tests.common.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playtomic.tests.common.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import static com.playtomic.tests.common.enums.PaymentDeliveryStatus.PAYMENT_DELIVERY_STATUS_APPLIED;
import static com.playtomic.tests.common.enums.PaymentDeliveryStatus.PAYMENT_DELIVERY_STATUS_ERROR;
import static com.playtomic.tests.common.enums.PaymentDeliveryStatus.PAYMENT_DELIVERY_STATUS_UNSPECIFIED;
import static com.playtomic.tests.common.enums.PaymentExecutionStatus.PAYMENT_EXECUTION_STATUS_ERROR;
import static com.playtomic.tests.common.enums.PaymentExecutionStatus.PAYMENT_EXECUTION_STATUS_SETTLED;
import static com.playtomic.tests.common.enums.PaymentExecutionStatus.PAYMENT_EXECUTION_STATUS_UNSPECIFIED;
import static com.playtomic.tests.common.enums.TransactionRegard.TRANSACTION_REGARD_ERROR;
import static com.playtomic.tests.common.enums.TransactionRegard.TRANSACTION_REGARD_UNSPECIFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnumLookupTest {
    @Test
    void jacksonPreservesPaymentStatusWireFormat() throws Exception {
        var mapper = new ObjectMapper();
        var deliveryJson = "{\"value\":3,\"name\":\"applied\"}";
        var executionJson = "{\"value\":5,\"name\":\"settled\"}";

        assertEquals(mapper.readTree(deliveryJson), mapper.valueToTree(PAYMENT_DELIVERY_STATUS_APPLIED));
        assertEquals(mapper.readTree(executionJson), mapper.valueToTree(PAYMENT_EXECUTION_STATUS_SETTLED));
        assertSame(PAYMENT_DELIVERY_STATUS_APPLIED, mapper.readValue(deliveryJson, PaymentDeliveryStatus.class));
        assertSame(PAYMENT_EXECUTION_STATUS_SETTLED, mapper.readValue(executionJson, PaymentExecutionStatus.class));
    }

    @ParameterizedTest
    @EnumSource(PaymentDeliveryStatus.class)
    @NullSource
    void parsesAndValidatesDeliveryStatuses(PaymentDeliveryStatus status) {
        if (status != null) {
            assertSame(status, PaymentDeliveryStatus.ofValue(status.getValue()));
            assertSame(status, PaymentDeliveryStatus.ofName(status.getName()));
        }
        if (status == null || status == PAYMENT_DELIVERY_STATUS_UNSPECIFIED || status == PAYMENT_DELIVERY_STATUS_ERROR) {
            assertIllegalArgument("Payment delivery status requires an operational value",
                    () -> PaymentDeliveryStatus.validate(status));
        } else {
            assertSame(status, PaymentDeliveryStatus.validate(status));
        }
    }

    @ParameterizedTest
    @EnumSource(PaymentExecutionStatus.class)
    @NullSource
    void parsesAndValidatesExecutionStatuses(PaymentExecutionStatus status) {
        if (status != null) {
            assertSame(status, PaymentExecutionStatus.ofValue(status.getValue()));
            assertSame(status, PaymentExecutionStatus.ofName(status.getName()));
        }
        if (status == null || status == PAYMENT_EXECUTION_STATUS_UNSPECIFIED || status == PAYMENT_EXECUTION_STATUS_ERROR) {
            assertIllegalArgument("Payment execution status requires an operational value",
                    () -> PaymentExecutionStatus.validate(status));
        } else {
            assertSame(status, PaymentExecutionStatus.validate(status));
        }
    }

    @ParameterizedTest
    @EnumSource(TransactionRegard.class)
    @NullSource
    void parsesAndValidatesTransactionRegards(TransactionRegard regard) {
        if (regard != null) {
            assertSame(regard, TransactionRegard.ofValue(regard.getValue()));
            assertSame(regard, TransactionRegard.ofName(regard.getName()));
        }
        if (regard == null || regard == TRANSACTION_REGARD_UNSPECIFIED || regard == TRANSACTION_REGARD_ERROR) {
            assertIllegalArgument("TransactionRegard requires a non-null operational value",
                    () -> TransactionRegard.validate(regard));
        } else {
            assertSame(regard, TransactionRegard.validate(regard));
        }
    }

    @Test
    void preservesUnknownNameAndValueErrors() {
        assertIllegalArgument("Unknown PaymentDeliveryStatus name: APPLIED",
                () -> PaymentDeliveryStatus.ofName("APPLIED"));
        assertIllegalArgument("Unknown PaymentDeliveryStatus value: 42",
                () -> PaymentDeliveryStatus.ofValue((short) 42));
        assertIllegalArgument("Unknown PaymentExecutionStatus name: null",
                () -> PaymentExecutionStatus.ofName(null));
        assertIllegalArgument("Unknown PaymentExecutionStatus value: 42",
                () -> PaymentExecutionStatus.ofValue((short) 42));
        assertIllegalArgument("unknown TransactionRegard! name: not-a-regard",
                () -> TransactionRegard.ofName("not-a-regard"));
        assertIllegalArgument("unknown TransactionRegard! value: 42",
                () -> TransactionRegard.ofValue((short) 42));
    }

    private static void assertIllegalArgument(String message, Executable operation) {
        var exception = assertThrows(BusinessException.class, operation);
        assertSame(CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, exception.getErrorCode());
        assertEquals(message, exception.getAdditionalInfo());
    }
}
