package com.playtomic.tests.wallet.constants;

import reactor.core.publisher.Mono;

import java.time.Duration;

/** Shared index initialization policy for background workers. */
public final class WalletWorkerParam {
    // Reactor treats a TTL of Long.MAX_VALUE milliseconds as indefinite.
    private static final Duration INDEX_SUCCESS_TTL = Duration.ofMillis(Long.MAX_VALUE);

    private WalletWorkerParam() {
    }

    public static <T> Mono<T> cacheSuccessfulIndex(Mono<T> source) {
        return source.cache(value -> INDEX_SUCCESS_TTL, error -> Duration.ZERO, () -> Duration.ZERO);
    }
}

