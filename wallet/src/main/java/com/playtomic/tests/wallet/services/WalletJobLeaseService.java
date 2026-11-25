package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.wallet.contracts.IWalletEntityAccess;
import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import com.playtomic.tests.wallet.mappers.WalletJobLeaseEntityMapper;
import com.playtomic.tests.wallet.models.WalletJobLeaseModel;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.models.WalletOutcome.Completion;
import com.playtomic.tests.wallet.repositories.WalletJobLeaseRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Getter(onMethod_ = @Override)
@Service
@RequiredArgsConstructor
public class WalletJobLeaseService implements IWalletEntityAccess<WalletJobLeaseModel, WalletJobLeaseEntity> {
    private final WalletJobLeaseRepository repository;
    private final WalletJobLeaseEntityMapper mapper;

    public Mono<Outcome<Optional<String>, Error>> acquire(String job, Duration duration) {
        return WalletOutcome.optional(Mono.defer(() -> {
            String token = UuidV7Generator.generate().toString();
            Instant now = Instant.now();
            return repository.acquire(job, now, now.plus(duration), token).map(WalletJobLeaseEntity::getToken);
        }).onErrorResume(DuplicateKeyException.class, ignored -> Mono.empty()));
    }

    public Mono<Outcome<Completion, Error>> release(String job, String token) {
        return WalletOutcome.completed(repository.release(job, token));
    }
}
