package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import lombok.RequiredArgsConstructor;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static com.playtomic.tests.wallet.enums.WalletOperationMongoType.TOP_UP;
import static org.springframework.data.domain.Sort.Direction.ASC;

@Repository
@RequiredArgsConstructor
class WalletOperationMongoOperations implements IWalletOperationMongoOperations {
    private static final String TYPE = "type";
    private static final String STATUS = "status";
    private static final String UPDATED_AT = "updatedAt";

    private final ReactiveMongoOperations mongo;

    /** Ensures the index used to find operations eligible for reconciliation. */
    @Override
    public Mono<String> ensureReconciliationIndex() {
        return mongo.indexOps(WalletOperationEntity.class)
                .createIndex(new Index().on(TYPE, ASC).on(STATUS, ASC).on(UPDATED_AT, ASC).on("_id", ASC));
    }

    /** Moves a top-up to the next state only when its current state is expected. */
    @Override
    public Mono<WalletOperationEntity> transitionTopUp(String id, WalletOperationMongoStatus next,
                                                       WalletOperationMongoStatus... expected) {
        var criteria = Criteria.where("id").is(id).and(TYPE).is(TOP_UP).and(STATUS).in(Arrays.asList(expected));
        return Mono.defer(() -> update(criteria, changeStatus(next)));
    }

    /** Records the final status and balance only if the expected state still matches. */
    @Override
    public Mono<WalletOperationEntity> finalizeOperation(String id, WalletOperationMongoStatus expected,
                                                          WalletOperationMongoStatus next, BigDecimal balance) {
        var criteria = Criteria.where("id").is(id).and(STATUS).is(expected);
        return Mono.defer(() -> update(criteria, changeStatus(next)
                .set("resultingBalance", balance == null ? null : new Decimal128(balance))));
    }

    private static Update changeStatus(WalletOperationMongoStatus next) {
        return new Update().set(STATUS, next).set(UPDATED_AT, Instant.now());
    }

    private Mono<WalletOperationEntity> update(Criteria criteria, Update update) {
        return mongo.findAndModify(Query.query(criteria), update, FindAndModifyOptions.options().returnNew(true),
                WalletOperationEntity.class);
    }
}