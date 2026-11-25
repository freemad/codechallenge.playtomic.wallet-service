package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface IWalletOutboxMongoOperations {
    Mono<String> ensurePublishIndex();

    Mono<WalletOutboxEntity> claim(Instant now, Instant availableAt, String token);
}