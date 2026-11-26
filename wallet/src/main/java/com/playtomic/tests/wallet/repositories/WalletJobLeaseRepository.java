package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Provides reactive persistence access for wallet job lease records. */
@Repository
public interface WalletJobLeaseRepository extends IRepository<WalletJobLeaseEntity, String> {
    /** Releases the named lease only when its ownership token matches. */
    Mono<Long> deleteByIdAndToken(String id, String token);
}