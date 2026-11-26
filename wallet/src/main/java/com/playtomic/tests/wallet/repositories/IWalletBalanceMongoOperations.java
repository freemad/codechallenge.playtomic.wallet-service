package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.wallet.entities.WalletEntity;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;

/** Defines currency-guarded balance changes and sequenced ledger posting. */
public interface IWalletBalanceMongoOperations {
    /** Applies the signed delta to a matching currency, subject to the optional minimum balance, and records the sequenced posting. */
    Mono<WalletEntity> incrementBalance(String id, String currency, BigDecimal delta, Optional<BigDecimal> minimumBalance);
}
