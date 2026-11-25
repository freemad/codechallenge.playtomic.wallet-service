package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.exceptions.WalletNotFoundException;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.models.WalletOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class WalletLookupService {

    private final WalletService wallets;

    public Mono<Outcome<WalletModel, Error>> findById(String id) {
        return WalletOutcome.from(requireWallet(id));
    }

    Mono<WalletModel> requireWallet(String id) {
        return Mono.defer(() -> {
            if (id == null || id.isBlank()) {
                return Mono.error(new BusinessException(WalletErrorCode.INVALID_WALLET_ID));
            }
            return wallets.findById(id).switchIfEmpty(Mono.error(WalletNotFoundException::new));
        });
    }
}