package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.contracts.IWalletEntityAccess;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.mappers.WalletEntityMapper;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.repositories.WalletRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;

import static com.playtomic.tests.wallet.enums.WalletErrorCode.BALANCE_UPDATE_NOT_MATCHED;

@Getter(onMethod_ = @Override)
@Service
@RequiredArgsConstructor
public class WalletService implements IWalletEntityAccess<WalletModel, WalletEntity> {
    private final WalletRepository repository;
    private final WalletEntityMapper mapper;

    /** A guard miss is a failure value; storage and mapping errors still fail the transaction. */
    Mono<Outcome<WalletModel, Error>> incrementBalance(String id, String currency, BigDecimal delta,
                                                       Optional<BigDecimal> minimumBalance) {
        return repository.incrementBalance(id, currency, delta, minimumBalance)
                .flatMap(entity -> mapper.toMonoModel(entity)
                        .switchIfEmpty(Mono.error(() -> new IllegalStateException("Updated wallet could not be mapped")))
                        .<Outcome<WalletModel, Error>>map(Outcome::success))
                .switchIfEmpty(Mono.fromSupplier(() -> Outcome.failure(new Error()
                        .setCode(BALANCE_UPDATE_NOT_MATCHED)
                        .setMessage(BALANCE_UPDATE_NOT_MATCHED.getMessage()))));
    }
}
