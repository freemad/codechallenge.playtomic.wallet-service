package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.services.entity.WalletEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class WalletLookupService {

    private final WalletEntityService entityService;
    private final WalletOutcomeHandler outcomeHandler;

    public Mono<Outcome<WalletModel, Error>> findById(String id) {
        return outcomeHandler.lookup(Mono.defer(() -> {
            if (id == null || id.isBlank()) {
                return Mono.error(new BusinessException(WalletErrorCode.INVALID_WALLET_ID));
            }
            return entityService.findById(id);
        }));
    }
}