package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.wallet.contracts.IWalletEntityService;
import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import com.playtomic.tests.wallet.mappers.WalletJobLeaseEntityMapper;
import com.playtomic.tests.wallet.models.WalletJobLeaseModel;
import com.playtomic.tests.wallet.repositories.IWalletJobLeaseMongoOperations;
import com.playtomic.tests.wallet.repositories.WalletJobLeaseRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WalletJobLeaseEntityService implements IWalletEntityService<WalletJobLeaseModel, WalletJobLeaseEntity> {
    @Getter(onMethod_ = @Override)
    private final WalletJobLeaseRepository repository;
    @Getter(onMethod_ = @Override)
    private final WalletJobLeaseEntityMapper mapper;
    private final IWalletJobLeaseMongoOperations mongoOperations;

    public Mono<String> acquire(String job, Duration duration) {
        return Mono.defer(() -> {
            String token = UuidV7Generator.generate().toString();
            Instant now = Instant.now();
            return mongoOperations.acquire(job, now, now.plus(duration), token);
        });
    }

    public Mono<Void> release(String job, String token) {
        return repository.deleteByIdAndToken(job, token).then();
    }
}
