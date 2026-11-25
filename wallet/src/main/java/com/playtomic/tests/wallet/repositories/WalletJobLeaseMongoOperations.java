package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface WalletJobLeaseMongoOperations {
    Mono<WalletJobLeaseEntity> acquire(String job, Instant now, Instant expiresAt, String token);

    Mono<Void> release(String job, String token);
}

