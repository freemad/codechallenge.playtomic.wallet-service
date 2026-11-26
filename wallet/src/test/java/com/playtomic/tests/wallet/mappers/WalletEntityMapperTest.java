package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.common.contracts.models.IEntity;
import com.playtomic.tests.common.contracts.models.IModel;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import com.playtomic.tests.wallet.entities.WalletPaymentReceiptEntity;
import com.playtomic.tests.wallet.enums.WalletEventType;
import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.enums.WalletOperationType;
import com.playtomic.tests.wallet.models.WalletJobLeaseModel;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.models.WalletPaymentReceiptModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WalletEntityMapperTest {
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private final WalletEntityMapper mapper = Mappers.getMapper(WalletEntityMapper.class);

    @Test
    void preservesOpaqueIdExactBalanceAndCurrencyInBothDirections() {
        var id = "507f1f77bcf86cd799439011";
        var balance = new BigDecimal("9007199254740993.01");
        assertRoundTrip(mapper, new WalletEntity(id, balance, "EUR"), new WalletModel(id, balance, "EUR"));
    }

    @Test
    void preservesPendingOperationWithNoResultingBalance() {
        var time = Instant.parse("2026-11-25T10:00:00Z");
        var amount = new BigDecimal("9007199254740993.01");
        var type = WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP;
        var status = WalletOperationStatus.WALLET_OPERATION_STATUS_PENDING;
        assertRoundTrip(Mappers.getMapper(WalletOperationEntityMapper.class),
                new WalletOperationEntity("op", "wallet", WalletOperationEntity.Type.TOP_UP, amount, "EUR",
                        WalletOperationEntity.Status.PENDING, null, time, "stripe"),
                new WalletOperationModel("op", "wallet", type, amount, "EUR", status, null, time, "stripe"));
    }

    @Test
    void preservesProviderPaymentIdAsReceiptIdentity() {
        var id = "507f1f77bcf86cd799439011";
        var time = Instant.parse("2026-11-25T10:00:00Z");
        assertRoundTrip(Mappers.getMapper(WalletPaymentReceiptEntityMapper.class),
                new WalletPaymentReceiptEntity(id, "op", time), new WalletPaymentReceiptModel(id, "op", time));
    }

    @Test
    void preservesOutboxEventAndDeliveryState() {
        var time = Instant.parse("2026-11-25T10:00:00Z");
        var available = time.plusSeconds(45);
        var balance = new BigDecimal("9007199254740993.01");
        var mapper = Mappers.getMapper(WalletOutboxEntityMapper.class);
        assertRoundTrip(mapper,
                new WalletOutboxEntity("op", "wallet", "WalletCredited", 1, BigDecimal.ONE, "EUR", balance,
                        time, available, "fence", null),
                new WalletOutboxModel("op", "wallet", WalletEventType.WALLET_CREDITED, 1, BigDecimal.ONE, "EUR",
                        balance, time, available, "fence", null));
        var published = time.plusSeconds(3);
        assertRoundTrip(mapper,
                new WalletOutboxEntity("op", "wallet", "WalletDebited", 2, BigDecimal.ONE, "EUR", balance,
                        time, available, null, published),
                new WalletOutboxModel("op", "wallet", WalletEventType.WALLET_DEBITED, 2, BigDecimal.ONE, "EUR",
                        balance, time, available, null, published));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"WALLET_CREDITED", "walletCredited", "WalletReversed"})
    void rejectsInvalidStoredOutboxEventType(String eventType) {
        var time = Instant.parse("2026-11-25T10:00:00Z");
        var entity = new WalletOutboxEntity("op", "wallet", eventType, 1, BigDecimal.ONE,
                "EUR", BigDecimal.ONE, time, time, null, null);
        var mapper = Mappers.getMapper(WalletOutboxEntityMapper.class);

        StepVerifier.create(mapper.toMonoModel(entity))
                .expectError(IllegalArgumentException.class).verify(DEADLINE);
    }

    @Test
    void preservesLeaseJobIdentityTokenAndExpiry() {
        var expiresAt = Instant.parse("2026-11-25T10:00:45Z");
        assertRoundTrip(Mappers.getMapper(WalletJobLeaseEntityMapper.class),
                new WalletJobLeaseEntity("payment-reconciliation", "fence", expiresAt),
                new WalletJobLeaseModel("payment-reconciliation", "fence", expiresAt));
    }

    @Test
    void reportsInvalidStoredDataOnSubscription() {
        var conversion = mapper.toMonoModel(new WalletEntity(" ", BigDecimal.ZERO, "EUR"));

        StepVerifier.create(conversion)
                .expectError(IllegalArgumentException.class).verify(DEADLINE);
    }

    @Test
    void mapsEveryStoredOperationTypeAndStatusInBothDirections() {
        var mapper = org.mapstruct.factory.Mappers.getMapper(WalletOperationEntityMapper.class);
        for (var stored : WalletOperationEntity.Type.values()) {
            var model = mapper.toModelType(stored);
            org.junit.jupiter.api.Assertions.assertEquals("WALLET_OPERATION_TYPE_" + stored.name(), model.name());
            org.junit.jupiter.api.Assertions.assertSame(stored, mapper.toEntityType(model));
        }
        for (var stored : WalletOperationEntity.Status.values()) {
            var model = mapper.toModelStatus(stored);
            org.junit.jupiter.api.Assertions.assertEquals("WALLET_OPERATION_STATUS_" + stored.name(), model.name());
            org.junit.jupiter.api.Assertions.assertSame(stored, mapper.toEntityStatus(model));
        }
    }

    @Test
    void rejectsSentinelsInsteadOfMappingThemToStoredValues() {
        var mapper = org.mapstruct.factory.Mappers.getMapper(WalletOperationEntityMapper.class);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> mapper.toEntityType(WalletOperationType.WALLET_OPERATION_TYPE_UNSPECIFIED));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> mapper.toEntityType(WalletOperationType.WALLET_OPERATION_TYPE_ERROR));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> mapper.toEntityStatus(WalletOperationStatus.WALLET_OPERATION_STATUS_UNSPECIFIED));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> mapper.toEntityStatus(WalletOperationStatus.WALLET_OPERATION_STATUS_ERROR));
    }

    private static <E extends IEntity<String>, M extends IModel> void assertRoundTrip(
            IEntityToModelMapper<E, M, String> mapper, E entity, M model) {
        entity.setNewRecord(true);
        StepVerifier.create(mapper.toMonoModel(entity))
                .expectNext(model).expectComplete().verify(DEADLINE);
        StepVerifier.create(mapper.toMonoEntity(model))
                .assertNext(mapped -> {
                    assertThat(mapped).isEqualTo(entity);
                    assertThat(mapped.getId()).isEqualTo(entity.getId());
                    assertThat(mapped.isNewRecord()).isFalse();
                })
                .expectComplete().verify(DEADLINE);
    }
}