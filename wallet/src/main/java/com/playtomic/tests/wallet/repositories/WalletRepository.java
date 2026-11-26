package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Provides reactive persistence access for wallet records. */
@Repository
public interface WalletRepository extends IRepository<WalletEntity, String> {
    /** Takes a transaction-local write fence on the existing wallet, without creating a posting. */
    @Query("{ '_id': ?0, 'currency': ?1 }")
    @Update("{ '$inc': { 'admissionVersion': 1 } }")
    Mono<Long> fenceAdmission(String id, String currency);
}