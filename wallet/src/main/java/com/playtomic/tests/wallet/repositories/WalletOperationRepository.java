package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import com.playtomic.tests.wallet.enums.WalletOperationMongoType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/** Provides reactive persistence access for wallet operation records. */
@Repository
public interface WalletOperationRepository extends IRepository<WalletOperationEntity, String> {
    /** Reads a page of matching operations no newer than the cutoff, ordered by update time and then identifier. */
    Flux<WalletOperationEntity> findByTypeAndStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAscIdAsc(
            WalletOperationMongoType type, WalletOperationMongoStatus status, Instant cutoff, Pageable page);

    /** Postpones recovery of a recorded receipt only if the observed operation has not changed. */
    default Mono<Long> deferReceiptRecovery(String id, Instant expectedUpdatedAt, Instant updatedAt) {
        return updateTimestampIfUnchanged(id, WalletOperationMongoType.TOP_UP,
                WalletOperationMongoStatus.RECEIPT_RECORDED, expectedUpdatedAt, updatedAt);
    }

    /** Moves the timestamp only when the operation type, status and previous timestamp still match. */
    @Query("{ '_id': ?0, 'type': ?1, 'status': ?2, 'updatedAt': ?3 }")
    @Update("{ '$set': { 'updatedAt': ?4 } }")
    Mono<Long> updateTimestampIfUnchanged(String id, WalletOperationMongoType type, WalletOperationMongoStatus status,
                                          Instant expectedUpdatedAt, Instant updatedAt);
}