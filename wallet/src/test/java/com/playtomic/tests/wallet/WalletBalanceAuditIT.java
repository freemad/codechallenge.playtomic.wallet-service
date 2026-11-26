package com.playtomic.tests.wallet;

import com.playtomic.tests.wallet.configs.WalletAuditProps;
import com.playtomic.tests.wallet.jobs.WalletBalanceAuditWorker;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import com.playtomic.tests.wallet.repositories.WalletAuditMongoOperations;
import com.playtomic.tests.wallet.services.WalletMutationService;
import com.playtomic.tests.wallet.services.adapter.StripeAdapter;
import com.playtomic.tests.wallet.services.entity.WalletBalanceAuditService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuditNetworkFixture.class)
class WalletBalanceAuditIT {
    private static final Duration LIMIT = Duration.ofSeconds(30);
    @Autowired ReactiveMongoTemplate mongo;
    @Autowired WalletBalanceAuditService audit;
    @Autowired WalletAuditMongoOperations store;
    @Autowired WalletMutationService mutations;
    @Autowired KafkaContainer broker;
    @Autowired KafkaTemplate<String, String> producer;
    @Autowired WalletAuditProps props;
    @Autowired JsonMapper json;
    @MockitoBean StripeAdapter stripe;
    @Autowired GenericContainer<?> auditProxy;

    @Test
    void delayedCompetingWorkersCheckTheHistoricalBalanceNotTheLiveBalance() {
        String id = "audit-correct";
        seed(id);
        when(stripe.charge(any())).thenReturn(Mono.just(new PaymentReceiptModel("audit-receipt")));
        var credit = mutations.topUp(id, "credit", new BigDecimal("5.25"), "EUR", "4242424242424242");
        assertThat(credit.block(LIMIT).getError()).isNull();
        assertThat(credit.block(LIMIT).getError()).isNull();
        assertThat(mutations.spend(id, "debit", new BigDecimal("2"), "EUR").block(LIMIT).getError()).isNull();
        Instant cutoff = closePeriod(id);
        when(stripe.charge(any())).thenReturn(Mono.just(new PaymentReceiptModel("later-receipt")));
        assertThat(mutations.topUp(id, "later", new BigDecimal("7"), "EUR", "4242424242424242").block(LIMIT).getError()).isNull();
        assertThat(Flux.merge(audit.auditNext(LIMIT), audit.auditNext(LIMIT)).collectList().block(LIMIT))
                .containsExactlyInAnyOrder(true, false);
        Document result = wallet(id);
        assertThat(money(result, "calculatedBalance")).isEqualByComparingTo("13.25");
        assertThat(money(result, "balance")).isEqualByComparingTo("20.25");
        assertThat(result.getLong("auditedLedgerSequence")).isEqualTo(2L);
        assertThat(result.getLong("ledgerSequence")).isEqualTo(3L);
        assertThat(result.getDate("cutoffAt").toInstant()).isEqualTo(cutoff);
        assertThat(result.getDate("calculationTimestamp").toInstant()).isAfterOrEqualTo(cutoff);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void takeoverPersistsOneDiscrepancyLogsItAndDeliversAnOpaqueKafkaAlert(CapturedOutput output) {
        String id = "audit-damaged";
        seed(id);
        assertThat(mutations.spend(id, "debit", BigDecimal.ONE, "EUR").block(LIMIT).getError()).isNull();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().inc("balance", new Decimal128(2)), "wallets").block(LIMIT);
        closePeriod(id);
        var stale = store.claim(Instant.now(), LIMIT).block(LIMIT);
        // Simulate expiration without a timing-dependent sleep; a replacement obtains a new token.
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("availableAt", new Date(0)), "wallet_audit_schedule").block(LIMIT);
        var winner = store.claim(Instant.now(), LIMIT).block(LIMIT);
        assertThat(audit.audit(stale).block(LIMIT)).isFalse();
        assertThat(audit.audit(winner).block(LIMIT)).isTrue();
        assertThat(audit.audit(winner).block(LIMIT)).isFalse();
        assertThat(wallet(id)).doesNotContainKeys("auditedLedgerSequence", "calculationTimestamp");
        assertThat(money(wallet(id), "balance")).isEqualByComparingTo("11");
        var evidence = mongo.find(Query.query(Criteria.where("walletId").is(id)), Document.class, "wallet_balance_audit").single().block(LIMIT);
        assertThat(money(evidence, "calculatedBalance")).isEqualByComparingTo("9");
        assertThat(money(evidence, "persistedBalance")).isEqualByComparingTo("11");
        assertThat(output.getOut().lines().filter(s -> s.contains("\"event\":\"audit.balance_discrepancy\""))).singleElement()
                .satisfies(s -> assertThat(s).doesNotContain(id, "persistedBalance", "calculatedBalance"));
        var worker = new WalletBalanceAuditWorker(audit, store, props, producer, json);
        worker.publish().block(LIMIT);
        String key = evidence.getString("_id");
        assertThat(mongo.findById(key, Document.class, "wallet_audit_alert_outbox").block(LIMIT).getDate("publishedAt")).isNotNull();
        try (var consumer = new KafkaConsumer<String,String>(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "audit-it", ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(props.getTopic()));
            long deadline = System.nanoTime() + LIMIT.toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    assertThat(record.key()).isEqualTo(key);
                    var payload = json.readTree(record.value());
                    assertThat(payload.propertyNames()).containsExactlyInAnyOrder("eventId", "eventType", "schemaVersion");
                    assertThat(payload.path("eventId").asString()).isEqualTo(key);
                    assertThat(payload.path("eventType").asString()).isEqualTo("WalletBalanceDiscrepancy");
                    return;
                }
            }
            throw new AssertionError("Missing audit alert");
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void mongoOutageRetriesSameCutoffUnderLatencyWithoutFalseDiscrepancy(CapturedOutput output) throws Exception {
        String id = "audit-network";
        seed(id);
        var worker = new WalletBalanceAuditWorker(audit, store, props, producer, json);
        worker.calculate().block(LIMIT); // Initialize indexes before the due period and the outage.
        long alertsBefore = mongo.count(new Query(), "wallet_audit_alert_outbox").block(LIMIT);
        proxyCommand("toxic", "add", "-t", "latency", "-a", "latency=60", "-n", "slow-replies", "mongo");
        try {
            var debit = mutations.spend(id, "network-debit", new BigDecimal("3.25"), "EUR");
            assertThat(debit.block(LIMIT).getError()).isNull();
            Instant cutoff = closePeriod(id);
            proxyCommand("toggle", "mongo");
            try {
                worker.calculate().block(LIMIT);
                assertThat(output.getOut()).contains("\"event\":\"audit.run_failed\"");
            } finally {
                proxyCommand("toggle", "mongo");
            }
            var pending = mongo.findById(id, Document.class, "wallet_audit_schedule").block(LIMIT);
            assertThat(pending.getDate("nextCutoff").toInstant()).isEqualTo(cutoff);
            assertThat(wallet(id)).doesNotContainKeys("auditedLedgerSequence", "calculationTimestamp");
            assertThat(debit.block(LIMIT).getError()).isNull(); // Replay after reconnection.
            worker.calculate().block(LIMIT);
            var result = wallet(id);
            assertThat(money(result, "balance")).isEqualByComparingTo("6.75");
            assertThat(money(result, "calculatedBalance")).isEqualByComparingTo("6.75");
            assertThat(result.getLong("ledgerSequence")).isEqualTo(1L);
            assertThat(result.getLong("auditedLedgerSequence")).isEqualTo(1L);
            assertThat(result.getDate("cutoffAt").toInstant()).isEqualTo(cutoff);
            var rows = Query.query(Criteria.where("walletId").is(id));
            assertThat(mongo.count(rows, "wallet_ledger").block(LIMIT)).isEqualTo(1L);
            assertThat(mongo.count(rows, "wallet_balance_audit").block(LIMIT)).isZero();
            assertThat(mongo.count(new Query(), "wallet_audit_alert_outbox").block(LIMIT)).isEqualTo(alertsBefore);
        } finally {
            proxyCommand("toxic", "remove", "-n", "slow-replies", "mongo");
        }
    }

    private void proxyCommand(String... args) throws Exception {
        var command = new ArrayList<>(List.of("/toxiproxy-cli"));
        command.addAll(List.of(args));
        var result = auditProxy.execInContainer(command.toArray(String[]::new));
        assertThat(result.getExitCode()).as("Toxiproxy: %s %s", result.getStdout(), result.getStderr()).isZero();
    }

    private void seed(String id) {
        mongo.insert(new Document("_id", id).append("currency", "EUR").append("balance", new Decimal128(10)), "wallets").block(LIMIT);
        store.indexes().block(LIMIT);
    }

    // Use the next real Mongo clock second instead of waiting for UTC midnight in CI.
    private Instant closePeriod(String id) {
        Instant cutoff = serverTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1);
        mongo.insert(new Document("_id", id).append("currency", "EUR").append("openingBalance", new Decimal128(10))
                .append("openingSequence", 0L).append("nextCutoff", Date.from(cutoff)).append("availableAt", Date.from(cutoff))
                .append("blocked", false), "wallet_audit_schedule").block(LIMIT);
        Mono.defer(() -> mongo.executeCommand(new Document("hello", 1)))
                .repeatWhen(completed -> completed.delayElements(Duration.ofMillis(50)))
                .filter(d -> !d.getDate("localTime").toInstant().isBefore(cutoff)).next().block(LIMIT);
        return cutoff;
    }
    private Instant serverTime() { return mongo.executeCommand(new Document("hello", 1)).block(LIMIT).getDate("localTime").toInstant(); }
    private Document wallet(String id) { return mongo.findById(id, Document.class, "wallets").block(LIMIT); }
    private BigDecimal money(Document d, String field) { return d.get(field, Decimal128.class).bigDecimalValue(); }
}