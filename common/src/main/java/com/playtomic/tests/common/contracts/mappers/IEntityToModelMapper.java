package com.playtomic.tests.common.contracts.mappers;


import com.playtomic.tests.common.contracts.models.IEntity;
import com.playtomic.tests.common.contracts.models.IModel;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.Objects;

/** Maps between persistence entities and application models, with deferred variants for reactive callers. */
public interface IEntityToModelMapper<T extends IEntity<I>, M extends IModel, I extends Serializable> {
    /** Maps the application model to its persistence representation. */
    T toEntity(M model);

    /** Maps the supplied representation to its application model. */
    M toModel(T entity);

    /** Defers entity conversion until subscription and rejects a null model. */
    default Mono<T> toMonoEntity(M model) {
        return Mono.fromCallable(() -> toEntity(Objects.requireNonNull(model, "model")));
    }

    /** Defers model conversion until subscription and rejects a null entity. */
    default Mono<M> toMonoModel(T entity) {
        return Mono.fromCallable(() -> toModel(Objects.requireNonNull(entity, "entity")));
    }
}
