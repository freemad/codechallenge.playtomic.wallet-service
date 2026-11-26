package com.playtomic.tests.common.contracts.models;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Exposes reactive repository operations in terms of models rather than persistence entities. */
public interface IEntityService<
        T extends IEntity<I>,
        M extends IModel,
        I extends Serializable> {

    /** Returns the repository backing these model operations. */
    IRepository<T, I> getRepository();

    /** Returns the deferred converter from a persistence entity to its model. */
    Function<T, Mono<M>> getMonoModelConverter();

    /** Returns the deferred converter from a model to its persistence entity. */
    Function<M, Mono<T>> getMonoEntityConverter();

    /** Looks up the model for the supplied identifier. */
    default Mono<M> findById(I id) {
        return getRepository().findById(id)
                .flatMap(getMonoModelConverter());
    }
    
    /** Streams models for identifiers found in the repository. */
    default Flux<M> findAllById(Collection<I> ids) {
        return getRepository().findAllById(ids)
                .flatMap(getMonoModelConverter());
    }
    
    /** Streams models for identifiers found in the repository. */
    default Flux<M> findAllById(Flux<I> ids) {
        return getRepository().findAllById(ids)
                .flatMap(getMonoModelConverter());
    }

    /** Collects the models found for the requested identifiers into one result. */
    default Mono<Collection<M>> findAllByIdAsCollection(Collection<I> ids) {
        return getRepository().findAllById(ids)
                .flatMap(getMonoModelConverter())
                .collect(Collectors.toList());
    }

    /** Streams all stored records as application models. */
    default Flux<M> findAll() {
        return getRepository().findAll()
                .flatMap(getMonoModelConverter());
    }

    /** Collects all stored models into one result. */
    default Mono<Collection<M>> findAllAsCollection() {
        return getRepository().findAll()
                .flatMap(getMonoModelConverter())
                .collect(Collectors.toList());
    }

    /** Persists the supplied model and returns its stored representation. */
    default Mono<M> save(M model) {
        return Mono.just(model)
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().save(t))
                .flatMap(getMonoModelConverter());
    }

    /** Persists the supplied model and returns its stored representation. */
    default Mono<M> save(Mono<M> eMono) {
        return eMono.flatMap(this::save);
    }

    /** Maps the model to an entity and saves it through the repository. */
    default Mono<M> update(M model) {
        return Mono.just(model)
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().save(t))
                .flatMap(getMonoModelConverter());
    }

    /** Persists the supplied models and emits the stored models. */
    default Flux<M> saveAll(Collection<M> models) {
        return Flux.fromStream(models.stream())
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().save(t))
                .flatMap(getMonoModelConverter());
    }

    /** Collects the stored models after saving the supplied batch. */
    default Mono<Collection<M>> saveAllAsCollection(Collection<M> models) {
        return saveAll(models).collect(Collectors.toList());
    }

    /** Persists the supplied models and emits the stored models. */
    default Flux<M> saveAll(Flux<M> mFlux) {
        return mFlux
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().save(t))
                .flatMap(getMonoModelConverter());
    }

    /** Collects the stored models after saving the supplied batch. */
    default Mono<Collection<M>> saveAllAsCollection(Flux<M> mFlux) {
        return saveAll(mFlux).collect(Collectors.toList());
    }

    /** Removes the record represented by the supplied model. */
    default Mono<Void> delete(M model) {
        return Mono.just(model)
                .flatMap(getMonoEntityConverter())
                .flatMap(t -> getRepository().delete(t));
    }

    /** Removes the specified records. */
    default Mono<Void> deleteAll(Collection<M> models) {
        return Flux.fromStream(models.stream())
                .flatMap(getMonoEntityConverter())
                .collectList()
                .flatMap(ts -> getRepository().deleteAll(ts));
    }

    /** Removes the specified records. */
    default Mono<Void> deleteAll(Flux<M> mFlux) {
        return mFlux
                .collectList()
                .flatMap(this::deleteAll);
    }

    /** Removes all records from the backing repository. */
    default Mono<Void> deleteAll() {
        return getRepository().deleteAll();
    }

    /** Removes the record identified by the supplied key. */
    default Mono<Void> deleteById(I id) {
        return getRepository().deleteById(id);
    }

    /** Removes records matching the supplied identifiers. */
    default Mono<Void> deleteAllById(Collection<I> ids) {
        return getRepository().deleteAllById(ids);
    }

    /** Counts records in the backing repository. */
    default Mono<Long> count() {
        return getRepository().count();
    }
}
