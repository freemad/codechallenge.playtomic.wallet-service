package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.springframework.data.domain.Sort.Direction.ASC;

@Repository
@RequiredArgsConstructor
class WalletOutboxMongoOperations implements IWalletOutboxMongoOperations {
    private static final String PUBLISHED_AT = "publishedAt";
    private static final String AVAILABLE_AT = "availableAt";

    private final ReactiveMongoOperations mongo;

    @Override
    public Mono<String> ensurePublishIndex() {
        return mongo.indexOps(WalletOutboxEntity.class)
                .createIndex(new Index().on(PUBLISHED_AT, ASC).on(AVAILABLE_AT, ASC).on("_id", ASC));
    }

    @Override
    public Mono<WalletOutboxEntity> claim(Instant now, Instant availableAt, String token) {
        var due = Query.query(Criteria.where(PUBLISHED_AT).isNull().and(AVAILABLE_AT).lte(now))
                .with(Sort.by(ASC, AVAILABLE_AT, "id"));
        var lease = new Update().set(AVAILABLE_AT, availableAt).set("leaseToken", token);
        return mongo.findAndModify(due, lease, FindAndModifyOptions.options().returnNew(true), WalletOutboxEntity.class);
    }
}
