package com.playtomic.tests.wallet;

import com.playtomic.tests.wallet.http.stripe.StripeService;
import com.playtomic.tests.wallet.services.WalletMutationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "wallet.outbox.enabled=true",
        "wallet.outbox.interval=250ms",
        "wallet.outbox.topic=" + WalletOutboxRelayIT.TOPIC,
        "wallet.reconciliation.enabled=false",
        "application.common.observability.metric-name-prefix="
})
@ActiveProfiles("test")
@Import(WalletOutboxRelayIT.Containers.class)
class WalletOutboxRelayIT {
    static final String TOPIC = "wallet-relay-it";
    private static final Duration DEADLINE = Duration.ofSeconds(30);
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @Autowired
    private WalletMutationService mutations;

    @Autowired
    private ReactiveMongoTemplate mongo;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private MeterRegistry metrics;

    @MockitoBean
    private StripeService stripe;

    @Test
    void publishesCommittedDebitAndAcknowledgesTheOutboxRow() {
        String walletId = "relay-test-wallet";
        var amount = new BigDecimal("0.02");
        var opening = new BigDecimal("9007199254740993.03");
        var wallet = new Document("_id", walletId).append("currency", "EUR")
                .append("balance", new Decimal128(opening));

        double resultsBefore = successfulSpendResults();
        long timersBefore = completedSpendTimers();
        var outcome = mongo.insert(wallet, "wallets")
                .then(mutations.spend(walletId, "relay-test-debit", amount, "EUR"))
                .block(DEADLINE);
        assertThat(outcome).isNotNull();
        assertThat(outcome.getError()).isNull();
        var result = outcome.getData();
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED);

        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "wallet-relay-it",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(TOPIC));
            var record = awaitRecord(consumer);
            assertThat(record.key()).isEqualTo(walletId);
            var payload = JSON.readTree(record.value());
            assertThat(payload.propertyNames()).containsExactlyInAnyOrder("eventId", "walletId", "eventType",
                    "schemaVersion", "amount", "currency", "resultingBalance", "occurredAt");
            assertThat(payload.path("eventId").asString()).isEqualTo(result.getId());
            assertThat(payload.path("walletId").asString()).isEqualTo(walletId);
            assertThat(payload.path("eventType").asString()).isEqualTo("WalletDebited");
            assertThat(payload.path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(payload.path("currency").asString()).isEqualTo("EUR");
            assertThat(payload.path("amount").isNumber()).isTrue();
            assertThat(payload.path("amount").decimalValue()).isEqualByComparingTo(amount);
            assertThat(payload.path("resultingBalance").isNumber()).isTrue();
            assertThat(payload.path("resultingBalance").decimalValue()).isEqualByComparingTo(opening.subtract(amount));
        }

        StepVerifier.create(Flux.interval(Duration.ZERO, Duration.ofMillis(100))
                        .concatMap(tick -> mongo.findById(result.getId(), Document.class, "wallet_outbox"))
                        .filter(event -> event.getDate("publishedAt") != null)
                        .next())
                .assertNext(event -> assertThat(event).doesNotContainKey("leaseToken"))
                .expectComplete().verify(DEADLINE);
        assertThat(successfulSpendResults()).isEqualTo(resultsBefore + 1);
        assertThat(completedSpendTimers()).isEqualTo(timersBefore + 1);
        verifyNoInteractions(stripe);
    }

    private static ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + DEADLINE.toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250)).iterator();
            if (records.hasNext()) {
                return records.next();
            }
        }
        throw new AssertionError("No wallet debit event received from Kafka within " + DEADLINE);
    }

    private double successfulSpendResults() {
        var counter = metrics.find("wallet.operation.results")
                .tags("operation", "SPEND", "status", "SUCCEEDED").counter();
        return counter == null ? 0 : counter.count();
    }

    private long completedSpendTimers() {
        var timer = metrics.find("wallet.spend.flow.duration")
                .tags("type", "Mono", "status", "completed", "exception", "").timer();
        return timer == null ? 0 : timer.count();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        MongoDBContainer mongoContainer() {
            return new MongoDBContainer(System.getProperty("wallet.test.mongo.image", "mongo:8.3.2-noble"))
                    .withReplicaSet();
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafkaContainer() {
            return new KafkaContainer(System.getProperty("wallet.test.kafka.image", "apache/kafka-native:4.0.0"));
        }

        @Bean
        NewTopic walletRelayTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }
    }
}