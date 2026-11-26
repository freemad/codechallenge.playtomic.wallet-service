package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.contracts.IWalletEntityService;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import com.playtomic.tests.wallet.enums.WalletOperationMongoType;
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
import java.util.List;

import static com.playtomic.tests.common.utils.ExceptionNormalizer.normalize;

/** Provides model-level access to wallet operation records. */
@Service
@RequiredArgsConstructor
public class WalletOperationEntityService implements IWalletEntityService<WalletOperationModel, WalletOperationEntity> {
    @Getter(onMethod_ = @Override)
    private final WalletOperationRepository repository;
    @Getter(onMethod_ = @Override)
    private final WalletOperationEntityMapper mapper;
    private final IWalletOperationMongoOperations mongoOperations;

    /** Looks up the model for the supplied identifier. */
    @Override
    public Mono<WalletOperationModel> findById(String id) {
        return repository.findById(id).flatMap(this::mapOperation);
    }

    /** Exposes unsettled credit amounts. */
    public Flux<BigDecimal> unsettledCredits(String walletId) {
        return repository.findByWalletIdAndTypeAndStatusIn(walletId, WalletOperationMongoType.TOP_UP,
                List.of(WalletOperationMongoStatus.PENDING, WalletOperationMongoStatus.REVIEW_REQUIRED,
                        WalletOperationMongoStatus.RECEIPT_RECORDED)).map(WalletOperationEntity::getAmount);
    }

    /** Attempts to reserve a new operation without overwriting another caller's reservation. */
    public Mono<WalletOperationModel> insertIfAbsent(WalletOperationModel request) {
        return Mono.defer(() -> {
            try {
                new Decimal128(request.getAmount()); // Reject values that the storage format would round.
            } catch (NumberFormatException cause) {
                return Mono.error(new BusinessException(WalletErrorCode.INVALID_REQUEST,
                        "Amount must be exactly representable as Decimal128", cause));
            }
            return getMonoEntityConverter().apply(request).flatMap(repository::insert)
                    .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Wallet operation was not inserted"))));
        }).flatMap(this::mapOperation);
    }

    /** Moves a top-up to the next state only when its current state is expected. */
    public Mono<WalletOperationModel> transitionTopUp(String id, WalletOperationStatus next, WalletOperationStatus... expected) {
        return Mono.defer(() -> mongoOperations.transitionTopUp(id, mapper.toEntityStatus(next),
                        Arrays.stream(expected).map(mapper::toEntityStatus).toArray(WalletOperationMongoStatus[]::new)))
                .flatMap(this::mapOperation);
    }

    /** Records the final status and balance only if the expected state still matches. */
    public Mono<WalletOperationModel> finalizeOperation(String id, WalletOperationStatus expected, WalletOperationStatus next,
                                                         BigDecimal balance) {
        return Mono.defer(() -> mongoOperations.finalizeOperation(id, mapper.toEntityStatus(expected),
                        mapper.toEntityStatus(next), balance))
                .flatMap(this::mapOperation);
    }

    /** Ensures the index used to find operations eligible for reconciliation. */
    public Mono<String> ensureReconciliationIndex() {
        return mongoOperations.ensureReconciliationIndex()
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Reconciliation index was not created"))));
    }

    /** Selects a bounded batch of operations in the requested status at or before the cutoff. */
    public Flux<WalletOperationModel> reconciliationBatch(WalletOperationStatus status, Instant cutoff, int limit) {
        return repository.findByTypeAndStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAscIdAsc(
                        WalletOperationMongoType.TOP_UP, mapper.toEntityStatus(status), cutoff, PageRequest.ofSize(limit))
                .concatMap(this::mapOperation);
    }

    /** Postpones recovery of a recorded receipt only if the observed operation has not changed. */
    public Mono<Void> deferReceiptRecovery(WalletOperationModel operation) {
        return Mono.defer(() -> repository.deferReceiptRecovery(operation.getId(), operation.getUpdatedAt(), Instant.now()))
                .then();
    }

    private Mono<WalletOperationModel> mapOperation(WalletOperationEntity entity) {
        return getMonoModelConverter().apply(entity)
                .switchIfEmpty(Mono.error(() -> normalize(new IllegalStateException("Wallet operation could not be mapped"))));
    }
}