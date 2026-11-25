package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.wallet.contracts.IWalletEntityService;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.mappers.WalletOperationEntityMapper;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.repositories.IWalletOperationMongoOperations;
import com.playtomic.tests.wallet.repositories.WalletOperationRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bson.types.Decimal128;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static com.playtomic.tests.common.utils.ExceptionNormalizer.normalize;

@Service
@RequiredArgsConstructor
public class WalletOperationEntityService implements IWalletEntityService<WalletOperationModel, WalletOperationEntity> {
    @Getter(onMethod_ = @Override)
    private final WalletOperationRepository repository;
    @Getter(onMethod_ = @Override)
    private final WalletOperationEntityMapper mapper;
    private final IWalletOperationMongoOperations mongoOperations;

    @Override
    public Mono<WalletOperationModel> findById(String id) {
        return repository.findById(id).flatMap(this::mapOperation);
    }

    public Mono<WalletOperationModel> insertIfAbsent(WalletOperationModel request) {
        return Mono.defer(() -> {
            new Decimal128(request.getAmount()); // Reject values that the storage format would round.
            return getMonoEntityConverter().apply(request).flatMap(repository::insert)
                    .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Wallet operation was not inserted"))));
        }).onErrorResume(DuplicateKeyException.class, ignored -> Mono.empty()).flatMap(this::mapOperation);
    }

    public Mono<WalletOperationModel> transitionTopUp(String id, WalletOperationStatus next, WalletOperationStatus... expected) {
        return Mono.defer(() -> mongoOperations.transitionTopUp(id, mapper.toEntityStatus(next),
                        Arrays.stream(expected).map(mapper::toEntityStatus).toArray(WalletOperationEntity.Status[]::new)))
                .flatMap(this::mapOperation);
    }

    public Mono<WalletOperationModel> finalizeOperation(String id, WalletOperationStatus expected, WalletOperationStatus next,
                                                         BigDecimal balance) {
        return Mono.defer(() -> mongoOperations.finalizeOperation(id, mapper.toEntityStatus(expected),
                        mapper.toEntityStatus(next), balance))
                .flatMap(this::mapOperation);
    }

    public Mono<String> ensureReconciliationIndex() {
        return mongoOperations.ensureReconciliationIndex()
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Reconciliation index was not created"))));
    }

    public Flux<WalletOperationModel> reconciliationBatch(WalletOperationStatus status, Instant cutoff, int limit) {
        return repository.findByTypeAndStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAscIdAsc(
                        WalletOperationEntity.Type.TOP_UP, mapper.toEntityStatus(status), cutoff, PageRequest.ofSize(limit))
                .concatMap(this::mapOperation);
    }

    public Mono<Void> deferReceiptRecovery(WalletOperationModel operation) {
        return Mono.defer(() -> repository.deferReceiptRecovery(operation.getId(), operation.getUpdatedAt(), Instant.now()))
                .then();
    }

    private Mono<WalletOperationModel> mapOperation(WalletOperationEntity entity) {
        return getMonoModelConverter().apply(entity)
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Wallet operation could not be mapped"))));
    }
}