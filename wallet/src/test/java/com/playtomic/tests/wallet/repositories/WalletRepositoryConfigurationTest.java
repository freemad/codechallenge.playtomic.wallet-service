package com.playtomic.tests.wallet.repositories;

import com.mongodb.reactivestreams.client.MongoClient;
import com.playtomic.tests.common.configs.CommonProps;
import com.playtomic.tests.common.contracts.models.IEntity;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import com.playtomic.tests.wallet.entities.WalletPaymentReceiptEntity;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import com.playtomic.tests.wallet.enums.WalletOperationMongoType;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"COMMON_RESILIENCE_BULKHEAD_MAX_CONCURRENT_CALLS=7", "WALLET_TEST_PROFILE=wallet-context-test"})
@ActiveProfiles("wallet-context-test")
class WalletRepositoryConfigurationTest {

    @MockitoBean
    private MongoClient mongoClient;

    @Autowired
    private WalletRepository documents;

    @Autowired
    private WalletOperationRepository operations;

    @Autowired
    private WalletPaymentReceiptRepository receipts;

    @Autowired
    private WalletOutboxRepository outbox;

    @Autowired
    private WalletJobLeaseRepository leases;

    @Autowired
    private ReactiveMongoTemplate mongo;

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CommonProps commonProps;

    @Test
    void loadsSharedYamlAndBindsOverridesWithoutChangingWalletProfileSettings() {
        assertThat(environment.getProperty("application.common.http.connect-timeout-ms")).isEqualTo("3000");
        assertThat(environment.getProperty("wallet.payment-providers.stripe.charges-uri")).isEqualTo("http://localhost:9999");
        assertThat(environment.getProperty("wallet.payment-providers.stripe.refunds-uri"))
                .isEqualTo("http://localhost:9999/payments/{payment_id}/refunds");
        assertThat(commonProps.getHttp().getConnectTimeoutMs()).isEqualTo(3000);
        assertThat(commonProps.getObservability().getMetricNamePrefix()).isEmpty();
        assertThat(commonProps.getResilience().getBulkhead().getMaxConcurrentCalls()).isEqualTo(7);
    }

    @Test
    void mapsStringIdsAndDecimalBalancesWithoutPersistingTheNewRecordFlag() {
        String id = "0123456789abcdef01234567";
        BigDecimal balance = new BigDecimal("9007199254740993.01");
        var wallet = new WalletEntity(id, balance, "EUR");
        wallet.setNewRecord(true);
        var bson = new Document();

        var converter = mongo.getConverter();
        converter.write(wallet, bson);

        assertThat(bson).containsEntry("_id", id)
                .containsEntry("balance", new Decimal128(balance))
                .containsEntry("currency", "EUR")
                .doesNotContainKeys("new", "newRecord");
        var loaded = converter.read(WalletEntity.class, bson);
        assertThat(loaded).isEqualTo(wallet);
        assertThat(loaded.isNew()).isFalse();
    }

    @Test
    void mapsMutationDocumentsWithoutAddingIdentityOrTrackingFields() {
        String id = "0123456789abcdef01234567";
        var time = Instant.parse("2026-11-24T00:00:00Z");
        var amount = new BigDecimal("0.02");
        var balance = new BigDecimal("9007199254740993.01");
        var operation = new WalletOperationEntity(id, "wallet", WalletOperationMongoType.TOP_UP, amount,
                "EUR", WalletOperationMongoStatus.SUCCEEDED, balance, time, "stripe");
        var receipt = new WalletPaymentReceiptEntity("payment-id", id, time);
        var outbox = new WalletOutboxEntity(id, "wallet", 1, 1, amount,
                "EUR", balance, time, time, null, null);

        assertThat(roundTrip(operation, WalletOperationEntity.class))
                .containsEntry("type", 1)
                .containsEntry("status", 4)
                .containsEntry("provider", "stripe")
                .containsEntry("amount", new Decimal128(amount))
                .containsEntry("resultingBalance", new Decimal128(balance));
        assertThat(roundTrip(receipt, WalletPaymentReceiptEntity.class))
                .containsEntry("operationId", id).doesNotContainKey("providerPaymentId");
        assertThat(roundTrip(outbox, WalletOutboxEntity.class))
                .containsEntry("eventType", 1)
                .containsEntry("schemaVersion", 1)
                .containsEntry("amount", new Decimal128(amount))
                .containsEntry("resultingBalance", new Decimal128(balance));
    }

    @ParameterizedTest
    @CsvSource({"TOP_UP,1,PENDING,1", "SPEND,2,REVIEW_REQUIRED,2", "TOP_UP,1,RECEIPT_RECORDED,3",
            "SPEND,2,SUCCEEDED,4", "TOP_UP,1,INSUFFICIENT_FUNDS,5", "SPEND,2,PAYMENT_REJECTED,6",
            "TOP_UP,1,PROVIDER_UNAVAILABLE,7", "SPEND,2,WALLET_UNAVAILABLE,8"})
    void roundTripsStableOperationCodes(WalletOperationMongoType type, int typeCode,
                                        WalletOperationMongoStatus status, int statusCode) {
        var operation = new WalletOperationEntity("op", "wallet", type, BigDecimal.ONE, "EUR", status,
                null, Instant.EPOCH, null);
        assertThat(roundTrip(operation, WalletOperationEntity.class))
                .containsEntry("type", typeCode).containsEntry("status", statusCode);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9, 255, 65537})
    void rejectsUnknownOperationCodes(int code) {
        var valid = new Document("type", 1).append("status", 1);
        for (String field : new String[]{"type", "status"}) {
            var invalid = new Document(valid).append(field, code);
            assertThatThrownBy(() -> mongo.getConverter().read(WalletOperationEntity.class, invalid))
                    .hasRootCauseInstanceOf(BusinessException.class);
        }
    }

    private <T extends IEntity<String>> Document roundTrip(T entity, Class<T> type) {
        entity.setNewRecord(true);
        assertThat(entity.isNew()).isTrue();
        var bson = new Document();
        var converter = mongo.getConverter();
        converter.write(entity, bson);
        assertThat(bson).containsEntry("_id", entity.getId()).doesNotContainKeys("id", "new", "newRecord");
        var loaded = converter.read(type, bson);
        assertThat(loaded).isEqualTo(entity);
        assertThat(loaded.isNew()).isFalse();
        var legacy = new Document(bson).append("_class", "com.playtomic.tests.wallet.repository." + type.getSimpleName());
        assertThat(converter.read(type, legacy)).isEqualTo(entity);
        return bson;
    }

    @Test
    void discoversAllWalletRepositoriesWithoutConnectingToMongo() {
        assertThat(documents).isNotNull();
        assertThat(operations).isNotNull();
        assertThat(receipts).isNotNull();
        assertThat(outbox).isNotNull();
        assertThat(leases).isNotNull();
    }

    @Test
    void discoversCustomOperationsAsSeparateBeans() {
        assertThat(context.getBeansOfType(IWalletBalanceMongoOperations.class)).hasSize(1);
        assertThat(context.getBean(IWalletBalanceMongoOperations.class)).isNotSameAs(documents);
        assertThat(context.getBeansOfType(IWalletOperationMongoOperations.class)).hasSize(1);
        assertThat(context.getBean(IWalletOperationMongoOperations.class)).isNotSameAs(operations);
        assertThat(context.getBeansOfType(IWalletOutboxMongoOperations.class)).hasSize(1);
        assertThat(context.getBean(IWalletOutboxMongoOperations.class)).isNotSameAs(outbox);
        assertThat(context.getBeansOfType(IWalletJobLeaseMongoOperations.class)).hasSize(1);
        assertThat(context.getBean(IWalletJobLeaseMongoOperations.class)).isNotSameAs(leases);
    }

    @Test
    void readsLegacyJobLeaseWithoutATypeHint() {
        var expiry = Instant.parse("2026-11-24T00:00:00Z");
        var bson = new Document("_id", "payment-reconciliation")
                .append("token", "lease-token").append("expiresAt", java.util.Date.from(expiry));
        assertThat(mongo.getConverter().read(WalletJobLeaseEntity.class, bson))
                .isEqualTo(new WalletJobLeaseEntity("payment-reconciliation", "lease-token", expiry));
    }
}