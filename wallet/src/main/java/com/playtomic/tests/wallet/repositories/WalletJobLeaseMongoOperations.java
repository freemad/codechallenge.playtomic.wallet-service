package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
class WalletJobLeaseMongoOperations implements IWalletJobLeaseMongoOperations {
    private static final String EXPIRES_AT = "expiresAt";

    private final ReactiveMongoOperations mongo;

    @Override
    public Mono<String> acquire(String job, Instant now, Instant expiresAt, String token) {
        var expired = Query.query(Criteria.where("id").is(job).and(EXPIRES_AT).lte(now));
        var lease = new Update().set("token", token).set(EXPIRES_AT, expiresAt);
        return mongo.findAndModify(expired, lease, FindAndModifyOptions.options().upsert(true).returnNew(true),
                        WalletJobLeaseEntity.class)
                .map(WalletJobLeaseEntity::getToken)
                // If an unexpired lease owns this _id, the attempted insert loses to that lease.
                .onErrorResume(DuplicateKeyException.class, ignored -> Mono.empty());
    }
}
