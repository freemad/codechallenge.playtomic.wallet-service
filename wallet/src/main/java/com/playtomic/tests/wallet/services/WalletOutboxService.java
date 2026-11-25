package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.utils.UuidV7Generator;
import com.playtomic.tests.wallet.contracts.IWalletEntityAccess;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import com.playtomic.tests.wallet.mappers.WalletOutboxEntityMapper;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.repositories.WalletOutboxRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.models.WalletOutcome.Completion;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Getter(onMethod_ = @Override)
@Service
@RequiredArgsConstructor
public class WalletOutboxService implements IWalletEntityAccess<WalletOutboxModel, WalletOutboxEntity> {
    private final WalletOutboxRepository repository;
    private final WalletOutboxEntityMapper mapper;

    Mono<Outcome<Completion, Error>> enqueue(WalletOutboxModel event) {
        return WalletOutcome.completed(WalletOutcome.required(mapper.toMonoEntity(event).flatMap(repository::insert),
                "Outbox event was not inserted").then());
    }

    public Mono<Outcome<String, Error>> ensurePublishIndex() {
        return WalletOutcome.required(repository.ensurePublishIndex(), "Outbox index was not created")
                .map(Outcome::success);
    }

    public Mono<Outcome<Optional<WalletOutboxModel>, Error>> claim(Duration lease) {
        return WalletOutcome.optional(Mono.defer(() -> {
            Instant now = Instant.now();
            return repository.claim(now, now.plus(lease), UuidV7Generator.generate().toString());
        }).flatMap(entity -> WalletOutcome.required(mapper.toMonoModel(entity), "Claimed event could not be mapped")));
    }

    public Mono<Outcome<Boolean, Error>> acknowledge(WalletOutboxModel event) {
        String token = event.getLeaseToken();
        Mono<Boolean> acknowledged = token == null || token.isBlank() ? Mono.just(false)
                : repository.acknowledge(event.getId(), token);
        return WalletOutcome.required(acknowledged, "Outbox acknowledgment returned no result").map(Outcome::success);
    }
}
