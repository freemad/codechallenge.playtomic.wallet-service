package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletEnumTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void usesCodeAndNameObjectsWithTheWalletJacksonMapper() {
        assertObjectEncoding(WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP, "{\"value\":1,\"name\":\"top_up\"}");
        assertObjectEncoding(WalletOperationStatus.WALLET_OPERATION_STATUS_REVIEW_REQUIRED,
                "{\"value\":2,\"name\":\"review_required\"}");
        assertObjectEncoding(WalletCompletion.WALLET_COMPLETION_COMPLETED, "{\"value\":1,\"name\":\"completed\"}");
    }

    @Test
    void looksUpSentinelsButRejectsThemAsOperationalValues() {
        var unspecifiedType = WalletOperationType.ofValue((short) 0);
        var errorStatus = WalletOperationStatus.ofName("error");
        var errorCompletion = WalletCompletion.ofValue((short) 255);

        assertThat(unspecifiedType).isSameAs(WalletOperationType.WALLET_OPERATION_TYPE_UNSPECIFIED);
        assertThat(errorStatus).isSameAs(WalletOperationStatus.WALLET_OPERATION_STATUS_ERROR);
        assertThat(errorCompletion).isSameAs(WalletCompletion.WALLET_COMPLETION_ERROR);
        assertThatThrownBy(() -> WalletOperationType.validate(unspecifiedType)).isInstanceOfSatisfying(
                BusinessException.class, error -> assertThat(error.getErrorCode()).isSameAs(CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION));
        assertThatThrownBy(() -> WalletOperationStatus.validate(errorStatus)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> WalletCompletion.validate(errorCompletion)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> WalletOperationType.ofName("TOP_UP")).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @CsvSource({"1,top_up", "2,spend"})
    void roundTripsOperationTypeAuditLabel(int code, String label) {
        var type = WalletOperationMongoType.ofValue(code);
        assertThat(type.getName()).isEqualTo(label);
        assertThat(WalletOperationMongoType.validate(WalletOperationMongoType.ofName(label))).isSameAs(type);
    }

    @ParameterizedTest
    @CsvSource({"1,pending", "2,review_required", "3,receipt_recorded", "4,succeeded", "5,insufficient_funds",
            "6,payment_rejected", "7,provider_unavailable", "8,wallet_unavailable"})
    void roundTripsOperationStatusAuditLabel(int code, String label) {
        var status = WalletOperationMongoStatus.ofValue(code);
        assertThat(status.getName()).isEqualTo(label);
        assertThat(WalletOperationMongoStatus.validate(WalletOperationMongoStatus.ofName(label))).isSameAs(status);
    }

    @Test
    void rejectsMissingOperationsAndNonCanonicalAuditLabels() {
        assertThatThrownBy(() -> WalletOperationMongoType.validate(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> WalletOperationMongoStatus.validate(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> WalletOperationMongoType.ofName("TOP_UP")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> WalletOperationMongoStatus.ofName("PENDING")).isInstanceOf(BusinessException.class);
    }

    private static void assertObjectEncoding(Enum<?> constant, String json) {
        assertThat(JSON.readTree(JSON.writeValueAsString(constant))).isEqualTo(JSON.readTree(json));
        assertThat(JSON.readValue(json, constant.getDeclaringClass())).isSameAs(constant);
    }
}