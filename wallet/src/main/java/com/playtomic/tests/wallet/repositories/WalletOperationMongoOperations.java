package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.models.WalletOperationModel.Status;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

public interface WalletOperationMongoOperations {
    Mono<String> ensureReconciliationIndex();

    Flux<WalletOperationEntity> reconciliationBatch(Status status, Instant cutoff, int limit);

    Mono<Void> deferReceiptRecovery(String id, Instant expectedUpdatedAt);

    Mono<WalletOperationEntity> transitionTopUp(String id, Status next, Status... expected);

    Mono<WalletOperationEntity> finalizeOperation(String id, Status expected, Status next, BigDecimal balance);
}

