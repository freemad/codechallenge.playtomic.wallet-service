package com.playtomic.tests.common.contracts.mappers;

import com.playtomic.tests.common.contracts.dtos.IDto;
import reactor.core.publisher.Mono;

import java.util.Objects;

public interface IModelToDtoMapper<M, D extends IDto> {
    M toModel(D dto);

    D toDto(M model);

    default Mono<M> toModelMono(D dto) {
        return Mono.fromCallable(() -> toModel(Objects.requireNonNull(dto, "dto")));
    }

    default Mono<D> toDtoMono(M model) {
        return Mono.fromCallable(() -> toDto(Objects.requireNonNull(model, "model")));
    }
}
