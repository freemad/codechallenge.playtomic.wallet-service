package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.contracts.IWalletEntityAccess;
import com.playtomic.tests.wallet.entities.WalletPaymentReceiptEntity;
import com.playtomic.tests.wallet.mappers.WalletPaymentReceiptEntityMapper;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.models.WalletOutcome.Completion;
import com.playtomic.tests.wallet.models.WalletPaymentReceiptModel;
import com.playtomic.tests.wallet.repositories.WalletPaymentReceiptRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Getter
@Service
@RequiredArgsConstructor
public class WalletPaymentReceiptService implements IWalletEntityAccess<WalletPaymentReceiptModel, WalletPaymentReceiptEntity> {
    private final WalletPaymentReceiptRepository repository;
    private final WalletPaymentReceiptEntityMapper mapper;

    // The caller owns the transaction that binds this receipt to its operation.
    Mono<Outcome<Completion, Error>> insert(WalletPaymentReceiptModel receipt) {
        return WalletOutcome.completed(WalletOutcome.required(mapper.toMonoEntity(receipt).flatMap(repository::insert),
                "Payment receipt was not inserted").then());
    }
}