package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.wallet.contracts.IWalletEntityService;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.mappers.WalletEntityMapper;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.repositories.IWalletBalanceMongoOperations;
import com.playtomic.tests.wallet.repositories.WalletRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bson.types.Decimal128;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;

import static com.playtomic.tests.common.utils.ExceptionNormalizer.normalize;

/** Provides model-level access to wallet records. */
@Service
@RequiredArgsConstructor
public class WalletEntityService implements IWalletEntityService<WalletModel, WalletEntity> {
    @Getter(onMethod_ = @Override)
    private final WalletRepository repository;
    @Getter(onMethod_ = @Override)
    private final WalletEntityMapper mapper;
    private final IWalletBalanceMongoOperations balanceOperations;

    /** Takes the admission fence and reads the wallet in the caller's transaction. */
    public Mono<WalletModel> fenceAdmission(String id, String currency) {
        return repository.fenceAdmission(id, currency).filter(count -> count == 1L)
                .flatMap(ignored -> findById(id))
                .switchIfEmpty(Mono.error(() -> new com.playtomic.tests.wallet.exceptions.WalletNotFoundException()));
    }

    /** A guard miss is empty; a present document must map or fail the transaction. */
    public Mono<WalletModel> incrementBalance(WalletModel snapshot, BigDecimal delta,
                                              Optional<BigDecimal> minimumBalance) {
        return Mono.defer(() -> {
            new Decimal128(snapshot.getBalance().add(delta)); // Reject rounding before issuing the write.
            return balanceOperations.incrementBalance(snapshot.getId(), snapshot.getCurrency(), delta, minimumBalance);
        }).flatMap(entity -> getMonoModelConverter().apply(entity)
                .switchIfEmpty(Mono.error(() -> normalize(
                        new IllegalStateException("Updated wallet could not be mapped")))));
    }
}