package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.entities.WalletPostingEntity;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
class WalletBalanceMongoOperations implements IWalletBalanceMongoOperations {
    private static final String BALANCE = "balance";

    private final ReactiveMongoOperations mongo;

    /** Uses the supplied reactive MongoDB client for balance and posting updates. */
    public WalletBalanceMongoOperations(ReactiveMongoOperations mongo) {
        this.mongo = mongo;
    }

    /** Applies the signed delta to a matching currency, subject to the optional minimum balance, and records the sequenced posting. */
    @Override
    public Mono<WalletEntity> incrementBalance(String id, String currency, BigDecimal delta,
                                               Optional<BigDecimal> minimumBalance) {
        return Mono.defer(() -> {
            var criteria = Criteria.where("id").is(id).and("currency").is(currency);
            minimumBalance.ifPresent(minimum -> criteria.and(BALANCE).gte(new Decimal128(minimum)));
            return mongo.findAndModify(Query.query(criteria), new Update().inc(BALANCE, new Decimal128(delta)).inc("ledgerSequence", 1L),
                    FindAndModifyOptions.options().returnNew(true), WalletEntity.class)
                    .flatMap(wallet -> mongo.insert(new WalletPostingEntity(id + ":" + wallet.getLedgerSequence(), id,
                            wallet.getLedgerSequence(), currency, delta)).thenReturn(wallet));
        });
    }
}