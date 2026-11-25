package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.wallet.contracts.IWalletEntityService;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import com.playtomic.tests.wallet.mappers.WalletOutboxEntityMapper;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.repositories.IWalletOutboxMongoOperations;
import com.playtomic.tests.wallet.repositories.WalletOutboxRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

import static com.playtomic.tests.common.utils.ExceptionNormalizer.normalize;

@Service
@RequiredArgsConstructor
public class WalletOutboxEntityService implements IWalletEntityService<WalletOutboxModel, WalletOutboxEntity> {
    @Getter(onMethod_ = @Override)
    private final WalletOutboxRepository repository;
    @Getter(onMethod_ = @Override)
    private final WalletOutboxEntityMapper mapper;
    private final IWalletOutboxMongoOperations mongoOperations;

    public Mono<Void> enqueue(WalletOutboxModel event) {
        return getMonoEntityConverter().apply(event).flatMap(repository::insert)
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Outbox event was not inserted"))))
                .then();
    }

    public Mono<String> ensurePublishIndex() {
        return mongoOperations.ensurePublishIndex()
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Outbox index was not created"))));
    }

    public Mono<WalletOutboxModel> claim(Duration lease) {
        return Mono.defer(() -> {
            Instant now = Instant.now();
            return mongoOperations.claim(now, now.plus(lease), UuidV7Generator.generate().toString());
        }).flatMap(entity -> getMonoModelConverter().apply(entity)
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Claimed event could not be mapped")))));
    }

    public Mono<Boolean> acknowledge(WalletOutboxModel event) {
        String token = event.getLeaseToken();
        if (token == null || token.isBlank()) {
            return Mono.just(false);
        }
        return Mono.defer(() -> repository.acknowledge(event.getId(), token, Instant.now()))
                .map(count -> count > 0)
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Outbox acknowledgment returned no result"))));
    }
}
