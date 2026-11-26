package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import reactor.core.publisher.Mono;

import java.time.Instant;

/** Defines atomic claims and index setup for wallet event publication. */
public interface IWalletOutboxMongoOperations {
    /** Ensures the index used to locate wallet events eligible for publication. */
    Mono<String> ensurePublishIndex();

    /** Leases one eligible unpublished wallet event for delivery. */
    Mono<WalletOutboxEntity> claim(Instant now, Instant availableAt, String token);
}