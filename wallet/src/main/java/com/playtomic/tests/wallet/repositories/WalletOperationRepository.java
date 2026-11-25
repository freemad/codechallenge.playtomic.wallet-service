package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.entities.WalletOperationEntity.Status;
import com.playtomic.tests.wallet.entities.WalletOperationEntity.Type;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface WalletOperationRepository extends IRepository<WalletOperationEntity, String> {
    Flux<WalletOperationEntity> findByTypeAndStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAscIdAsc(
            Type type, Status status, Instant cutoff, Pageable page);

    @Query("{ '_id': ?0, 'type': 'TOP_UP', 'status': 'RECEIPT_RECORDED', 'updatedAt': ?1 }")
    @Update("{ '$set': { 'updatedAt': ?2 } }")
    Mono<Long> deferReceiptRecovery(String id, Instant expectedUpdatedAt, Instant updatedAt);
}