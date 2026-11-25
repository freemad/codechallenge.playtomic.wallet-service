package com.playtomic.tests.wallet.contracts;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.common.contracts.models.IEntity;
import com.playtomic.tests.common.contracts.models.IEntityService;
import com.playtomic.tests.common.contracts.models.IModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.function.Function;

/** Reuses shared reads and mapping; service writes require dedicated commands. */
public interface IWalletEntityAccess<M extends IModel, E extends IEntity<String>> extends IEntityService<E, M, String> {
    IEntityToModelMapper<E, M, String> getMapper();

    @Override
    default Function<E, Mono<M>> getMonoModelConverter() {
        return getMapper()::toMonoModel;
    }

    @Override
    default Function<M, Mono<E>> getMonoEntityConverter() {
        return getMapper()::toMonoEntity;
    }

    @Override
    default Mono<M> save(M model) {
        return Mono.error(unsupportedWrite());
    }

    @Override
    default Mono<M> save(Mono<M> model) {
        return Mono.error(unsupportedWrite());
    }

    @Override
    default Mono<M> update(M model) {
        return Mono.error(unsupportedWrite());
    }

    @Override
    default Flux<M> saveAll(Collection<M> models) {
        return Flux.error(unsupportedWrite());
    }

    @Override
    default Flux<M> saveAll(Flux<M> models) {
        return Flux.error(unsupportedWrite());
    }

    @Override
    default Mono<Void> delete(M model) {
        return Mono.error(unsupportedWrite());
    }

    @Override
    default Mono<Void> deleteAll(Collection<M> models) {
        return Mono.error(unsupportedWrite());
    }

    @Override
    default Mono<Void> deleteAll(Flux<M> models) {
        return Mono.error(unsupportedWrite());
    }

    @Override
    default Mono<Void> deleteAll() {
        return Mono.error(unsupportedWrite());
    }

    @Override
    default Mono<Void> deleteById(String id) {
        return Mono.error(unsupportedWrite());
    }

    @Override
    default Mono<Void> deleteAllById(Collection<String> ids) {
        return Mono.error(unsupportedWrite());
    }

    private static UnsupportedOperationException unsupportedWrite() {
        return new UnsupportedOperationException("Wallet writes require a dedicated command");
    }
}
