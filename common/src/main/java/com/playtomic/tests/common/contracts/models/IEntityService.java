package com.playtomic.tests.common.contracts.models;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Function;

public interface IEntityService<
        M extends IModel,
        T extends IEntity<I>,
        I extends Serializable> {

    IRepository<T, I> getRepository();

    Function<T, Mono<M>> getMonoModelConverter();

    Function<M, Mono<T>> getMonoEntityConverter();

    Mono<M> findById(I id);

    Flux<M> findAllById(Collection<I> ids);

    Flux<M> findAllById(Flux<I> ids);

    Flux<M> findAll();

    Mono<M> save(M model);

    Mono<M> save(Mono<M> mMono);

    Mono<M> update(M model);

    Flux<M> saveAll(Collection<M> models);

    Flux<M> saveAll(Flux<M> mFlux);

    Mono<Void> delete(M model);

    Mono<Void> deleteAll(Collection<M> models);

    Mono<Void> deleteAll(Flux<M> mFlux);

    Mono<Void> deleteAll();

    Mono<Void> deleteById(I id);

    Mono<Void> deleteAllById(Collection<I> ids);

    Mono<Long> count();
}
