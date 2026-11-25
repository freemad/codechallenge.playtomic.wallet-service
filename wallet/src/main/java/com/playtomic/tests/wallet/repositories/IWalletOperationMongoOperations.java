package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.entities.WalletOperationEntity.Status;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface IWalletOperationMongoOperations {
    Mono<String> ensureReconciliationIndex();

    Mono<WalletOperationEntity> transitionTopUp(String id, Status next, Status... expected);

    Mono<WalletOperationEntity> finalizeOperation(String id, Status expected, Status next, BigDecimal balance);
}
