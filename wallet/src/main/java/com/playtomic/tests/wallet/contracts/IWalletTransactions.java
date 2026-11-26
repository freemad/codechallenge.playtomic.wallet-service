package com.playtomic.tests.wallet.contracts;

import reactor.core.publisher.Mono;

/** Transaction support for local wallet writes; never wrap remote payment calls. */
public interface IWalletTransactions {
    /** Executes the supplied reactive work in a wallet transaction. */
    <T> Mono<T> execute(Mono<T> work);

    /** Reports whether the failure represents a duplicate persistence key. */
    boolean isDuplicateKey(Throwable error);
}

