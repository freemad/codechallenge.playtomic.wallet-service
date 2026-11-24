package com.playtomic.tests.common.contracts.services;

import com.playtomic.tests.common.contracts.models.IEntity;
import com.playtomic.tests.common.contracts.models.IModel;
import com.playtomic.tests.common.contracts.models.IRepository;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface IEntityService<
        M extends IModel,
        T extends IEntity<I>,
        I extends Serializable> {

    public abstract Logger getLogger();

    IRepository<T, I> getRepository();


    Function<T, Mono<M>> getMonoModelConverter();

    Function<M, Mono<T>> getMonoEntityConverter();

    default Mono<M> findById(I id) {
        return getRepository().findById(id)
                .flatMap(getMonoModelConverter());
    }

    default Flux<M> findAllById(Collection<I> ids) {
        return getRepository().findAllById(ids)
                .flatMap(getMonoModelConverter());
    }

    default Mono<Collection<M>> findAllByIdAsCollection(Collection<I> ids) {
        return findAllById(ids).collect(Collectors.toList());
    }

    default Flux<M> findAllById(Flux<I> ids) {
        return getRepository().findAllById(ids)
                .flatMap(getMonoModelConverter());
    }

    default Mono<Collection<M>> findAllByIdAsCollection(Flux<I> ids) {
        return findAllById(ids).collect(Collectors.toList());
    }

    default Flux<M> findAll() {
        return getRepository().findAll()
                .flatMap(getMonoModelConverter());
    }

    default Mono<Collection<M>> findAllAsCollection() {
        return findAll().collect(Collectors.toList());
    }

    default Mono<M> save(M model) {
        return Mono.just(model)
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().save(t))
                .flatMap(getMonoModelConverter());
    }

    default Mono<M> save(Mono<M> eMono) {
        return eMono.flatMap(this::save);
    }

    default Mono<M> update(M model) {
        return Mono.just(model)
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().save(t))
                .flatMap(getMonoModelConverter());
    }

    default Flux<M> saveAll(Collection<M> models) {
        return Flux.fromStream(models.stream())
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().save(t))
                .flatMap(getMonoModelConverter());
    }

    default Flux<M> saveAll(Flux<M> mFlux) {
        return mFlux
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().save(t))
                .flatMap(getMonoModelConverter());
    }

    default Mono<Void> delete(M model) {
        return Mono.just(model)
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().delete(t));
    }

    default Mono<Void> deleteAll(Collection<M> models) {
        return Flux.fromStream(models.stream())
                .flatMap(getMonoEntityConverter())
                .collectList()
                .flatMap(ts -> getRepository().deleteAll(ts));
    }

    default Mono<Void> deleteAll(Flux<M> mFlux) {
        return mFlux
                .collectList()
                .flatMap(this::deleteAll);
    }

    default Mono<Void> deleteAll() {
        return getRepository().deleteAll();
    }

    default Mono<Void> deleteById(I id) {
        return getRepository().deleteById(id);
    }

    default Mono<Void> deleteAllById(Collection<I> ids) {
        return getRepository().deleteAllById(ids);
    }

    default Mono<Long> count() {
        return getRepository().count();
    }
}
