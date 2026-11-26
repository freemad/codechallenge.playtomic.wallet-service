package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface IWalletOperationMongoOperations {
    Mono<String> ensureReconciliationIndex();

    Mono<WalletOperationEntity> transitionTopUp(String id, WalletOperationMongoStatus next,
                                                WalletOperationMongoStatus... expected);

    Mono<WalletOperationEntity> finalizeOperation(String id, WalletOperationMongoStatus expected,
                                                   WalletOperationMongoStatus next, BigDecimal balance);
}