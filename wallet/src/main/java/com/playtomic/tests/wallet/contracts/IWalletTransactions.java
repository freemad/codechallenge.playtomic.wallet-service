package com.playtomic.tests.wallet.contracts;

import reactor.core.publisher.Mono;

/** Transaction support for local wallet writes; never wrap remote payment calls. */
public interface IWalletTransactions {
    <T> Mono<T> execute(Mono<T> work);

    boolean isDuplicateKey(Throwable error);
}

