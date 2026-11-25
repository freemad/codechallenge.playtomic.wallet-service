package com.playtomic.tests.common.contracts.mappers;


import com.playtomic.tests.common.contracts.models.IEntity;
import com.playtomic.tests.common.contracts.models.IModel;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.Objects;

public interface IEntityToModelMapper<T extends IEntity<I>, M extends IModel, I extends Serializable> {
    T toEntity(M model);

    M toModel(T entity);

    default Mono<T> toMonoEntity(M model) {
        return Mono.fromCallable(() -> toEntity(Objects.requireNonNull(model, "model")));
    }

    default Mono<M> toMonoModel(T entity) {
        return Mono.fromCallable(() -> toModel(Objects.requireNonNull(entity, "entity")));
    }
}
