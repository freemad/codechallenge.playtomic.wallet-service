package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.wallet.contracts.IWalletEntityService;
import com.playtomic.tests.wallet.entities.WalletPaymentReceiptEntity;
import com.playtomic.tests.wallet.mappers.WalletPaymentReceiptEntityMapper;
import com.playtomic.tests.wallet.models.WalletPaymentReceiptModel;
import com.playtomic.tests.wallet.repositories.WalletPaymentReceiptRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.playtomic.tests.common.utils.ExceptionNormalizer.normalize;

@Getter
@Service
@RequiredArgsConstructor
public class WalletPaymentReceiptEntityService implements IWalletEntityService<WalletPaymentReceiptModel, WalletPaymentReceiptEntity> {
    private final WalletPaymentReceiptRepository repository;
    private final WalletPaymentReceiptEntityMapper mapper;

    // The caller owns the transaction that binds this receipt to its operation.
    public Mono<Void> insert(WalletPaymentReceiptModel receipt) {
        return getMonoEntityConverter().apply(receipt).flatMap(repository::insert)
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Payment receipt was not inserted"))))
                .then();
    }
}