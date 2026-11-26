package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.wallet.entities.WalletAuditScheduleEntity;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.entities.WalletPostingEntity;
import lombok.RequiredArgsConstructor;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import static org.springframework.data.domain.Sort.Direction.ASC;

@Repository
@RequiredArgsConstructor
public class WalletAuditMongoOperations {
    private static final String ALERTS = "wallet_audit_alert_outbox";
    private final ReactiveMongoOperations mongo;

    public Mono<String> indexes() {
        return mongo.indexOps(WalletPostingEntity.class).createIndex(new Index().on("walletId", ASC).on("sequence", ASC).unique())
                .then(mongo.indexOps(WalletAuditScheduleEntity.class).createIndex(new Index().on("blocked", ASC).on("nextCutoff", ASC)))
                .then(mongo.indexOps(ALERTS).createIndex(new Index().on("publishedAt", ASC).on("availableAt", ASC)));
    }

    public Mono<WalletAuditScheduleEntity> claim(Instant now, Duration lease) {
        var query = Query.query(Criteria.where("blocked").is(false).and("nextCutoff").lte(now).and("availableAt").lte(now))
                .with(Sort.by("nextCutoff", "id"));
        return mongo.findAndModify(query, claimUpdate(now, lease), FindAndModifyOptions.options().returnNew(true), WalletAuditScheduleEntity.class);
    }

    /** Last logical tick before cut-off, not process start or transaction invocation time. */
    public Mono<WalletEntity> atCutoff(WalletAuditScheduleEntity claim) {
        var timestamp = new BsonTimestamp(Math.toIntExact(claim.getNextCutoff().getEpochSecond() - 1), -1);
        var command = new Document("find", "wallets").append("filter", new Document("_id", claim.getId()))
                .append("limit", 1).append("singleBatch", true).append("maxTimeMS", 15000)
                .append("readConcern", new Document("level", "snapshot").append("atClusterTime", timestamp));
        return mongo.executeCommand(command).map(result -> {
            var batch = result.get("cursor", Document.class).getList("firstBatch", Document.class);
            if (batch.isEmpty()) throw new IllegalStateException("Missing cut-off wallet snapshot");
            return mongo.getConverter().read(WalletEntity.class, batch.getFirst());
        });
    }

    public Flux<WalletPostingEntity> postings(WalletAuditScheduleEntity claim, long through) {
        return mongo.find(Query.query(Criteria.where("walletId").is(claim.getId()).and("sequence")
                .gt(claim.getOpeningSequence()).lte(through)).with(Sort.by("sequence")), WalletPostingEntity.class);
    }

    /** First write of the result transaction. A stale or replaced claim cannot produce any side effect. */
    public Mono<Boolean> fence(WalletAuditScheduleEntity claim, boolean matched, Instant now) {
        var criteria = Criteria.where("id").is(claim.getId()).and("token").is(claim.getToken())
                .and("nextCutoff").is(claim.getNextCutoff()).and("availableAt").gt(now);
        var next = new Update().unset("token").set("blocked", !matched).set("availableAt", now);
        if (matched) next.set("nextCutoff", claim.getNextCutoff().plus(Duration.ofDays(1)));
        return mongo.updateFirst(Query.query(criteria), next, WalletAuditScheduleEntity.class).map(r -> r.getModifiedCount() == 1);
    }

    public Mono<Void> checkpoint(WalletAuditScheduleEntity claim, long sequence, BigDecimal balance, Instant now) {
        return mongo.updateFirst(Query.query(Criteria.where("id").is(claim.getId())), new Update()
                .set("auditedLedgerSequence", sequence).set("calculatedBalance", new Decimal128(balance))
                .set("calculationTimestamp", now).set("cutoffAt", claim.getNextCutoff()), WalletEntity.class)
                .flatMap(r -> r.getMatchedCount() == 1 ? Mono.empty() : Mono.error(new IllegalStateException("Missing audit wallet")));
    }

    public Mono<Void> discrepancy(WalletAuditScheduleEntity claim, WalletEntity snapshot, BigDecimal calculated, boolean continuous, Instant now) {
        var eventId = UuidV7Generator.generate().toString();
        var evidence = new Document("_id", eventId).append("walletId", claim.getId()).append("currency", claim.getCurrency())
                .append("persistedCurrency", snapshot.getCurrency()).append("cutoffAt", Date.from(claim.getNextCutoff()))
                .append("calculationTimestamp", Date.from(now)).append("ledgerSequence", snapshot.getLedgerSequence())
                .append("persistedBalance", new Decimal128(snapshot.getBalance())).append("calculatedBalance", new Decimal128(calculated))
                .append("continuous", continuous).append("result", "balance_discrepancy");
        var alert = new Document("_id", eventId).append("availableAt", Date.from(now));
        return mongo.insert(evidence, "wallet_balance_audit").then(mongo.insert(alert, ALERTS)).then();
    }

    public Mono<Document> claimAlert(Instant now, Duration lease) {
        return mongo.findAndModify(Query.query(Criteria.where("publishedAt").isNull().and("availableAt").lte(now))
                        .with(Sort.by("availableAt")), claimUpdate(now, lease), FindAndModifyOptions.options().returnNew(true), Document.class, ALERTS);
    }

    public Mono<Void> acknowledge(Document claim) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(claim.getString("_id")).and("token").is(claim.getString("token"))),
                new Update().currentDate("publishedAt").unset("token"), ALERTS).then();
    }

    private static Update claimUpdate(Instant now, Duration lease) {
        return new Update().set("availableAt", Date.from(now.plus(lease))).set("token", UuidV7Generator.generate().toString());
    }
}