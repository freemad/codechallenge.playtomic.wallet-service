package com.playtomic.tests.common.enums;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    @Test
    void jacksonRoundTripsReviewEnumsAndTransactionRegard() throws Exception {
        assertObjectEncoding(PaymentReviewOperation.PAYMENT_REVIEW_OPERATION_PROCEED,
                "{\"value\":3,\"name\":\"proceed\"}");
        assertObjectEncoding(PaymentReviewStatus.PAYMENT_REVIEW_STATUS_RESOLUTION_REQUESTED,
                "{\"value\":3,\"name\":\"resolution_requested\"}");
        assertObjectEncoding(TransactionRegard.TRANSACTION_REGARD_PURCHASE,
                "{\"value\":2,\"name\":\"purchase\"}");
    }

    @Test
    void reviewEnumsSeparateParsingFromOperationalValidation() {
        for (var operation : PaymentReviewOperation.values()) {
            assertSame(operation, PaymentReviewOperation.ofValue(operation.getValue()));
            assertSame(operation, PaymentReviewOperation.ofName(operation.getName()));
        }
        for (var status : PaymentReviewStatus.values()) {
            assertSame(status, PaymentReviewStatus.ofValue(status.getValue()));
            assertSame(status, PaymentReviewStatus.ofName(status.getName()));
        }
        assertIllegalArgument("Payment review operation requires an operational value",
                () -> PaymentReviewOperation.validate(PaymentReviewOperation.PAYMENT_REVIEW_OPERATION_ERROR));
        assertIllegalArgument("Payment review status requires an operational value",
                () -> PaymentReviewStatus.validate(PaymentReviewStatus.PAYMENT_REVIEW_STATUS_UNSPECIFIED));
    }

    private static void assertObjectEncoding(Enum<?> constant, String json) throws JsonProcessingException {
        var mapper = new ObjectMapper();
        assertEquals(mapper.readTree(json), mapper.valueToTree(constant));
        assertSame(constant, mapper.readValue(json, constant.getDeclaringClass()));
        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue("{\"name\":\"unspecified\"}", constant.getDeclaringClass()));
    }

    private static void assertIllegalArgument(String message, Executable operation) {
        var exception = assertThrows(BusinessException.class, operation);
        assertSame(CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, exception.getErrorCode());
        assertEquals(message, exception.getAdditionalInfo());
    }
}