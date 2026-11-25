package com.playtomic.tests.wallet.repositories;

import reactor.core.publisher.Mono;

import java.time.Instant;

public interface IWalletJobLeaseMongoOperations {
    Mono<String> acquire(String job, Instant now, Instant expiresAt, String token);
}
