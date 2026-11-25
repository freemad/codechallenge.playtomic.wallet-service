package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface WalletOutboxRepository extends IRepository<WalletOutboxEntity, String> {
    @Query("{ '_id': ?0, 'leaseToken': ?1, 'publishedAt': null }")
    @Update("{ '$set': { 'publishedAt': ?2 }, '$unset': { 'leaseToken': '' } }")
    Mono<Long> acknowledge(String id, String token, Instant publishedAt);
}