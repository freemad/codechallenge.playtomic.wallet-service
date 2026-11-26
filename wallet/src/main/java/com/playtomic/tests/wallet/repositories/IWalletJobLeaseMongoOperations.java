package com.playtomic.tests.wallet.repositories;

import reactor.core.publisher.Mono;

import java.time.Instant;

/** Defines atomic acquisition of a time-limited wallet worker lease. */
public interface IWalletJobLeaseMongoOperations {
    /** Attempts to obtain the job lease and returns the token when acquired. */
    Mono<String> acquire(String job, Instant now, Instant expiresAt, String token);
}
