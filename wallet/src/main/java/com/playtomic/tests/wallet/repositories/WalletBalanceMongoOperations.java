package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletEntity;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;

public interface WalletBalanceMongoOperations {
    Mono<WalletEntity> incrementBalance(String id, String currency, BigDecimal delta, Optional<BigDecimal> minimumBalance);
}

