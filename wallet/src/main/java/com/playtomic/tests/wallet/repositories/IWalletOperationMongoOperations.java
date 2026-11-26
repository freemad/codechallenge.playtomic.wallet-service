package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/** Defines conditional transitions that prevent concurrent callers from finalizing an operation twice. */
public interface IWalletOperationMongoOperations {
    /** Ensures the index used to find operations eligible for reconciliation. */
    Mono<String> ensureReconciliationIndex();

    /** Moves a top-up to the next state only when its current state is expected. */
    Mono<WalletOperationEntity> transitionTopUp(String id, WalletOperationMongoStatus next,
                                                WalletOperationMongoStatus... expected);

    /** Records the final status and balance only if the expected state still matches. */
    Mono<WalletOperationEntity> finalizeOperation(String id, WalletOperationMongoStatus expected,
                                                   WalletOperationMongoStatus next, BigDecimal balance);
}