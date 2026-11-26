package com.playtomic.tests.common.contracts.mappers;

import com.playtomic.tests.common.contracts.dtos.IDto;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** Maps between application models and transport objects, with deferred variants for reactive callers. */
public interface IModelToDtoMapper<M, D extends IDto> {
    /** Maps the supplied representation to its application model. */
    M toModel(D dto);

    /** Maps the supplied value to its transport representation. */
    D toDto(M model);

    /** Defers model conversion until subscription; a null input fails and a null conversion result completes empty. */
    default Mono<M> toModelMono(D dto) {
        return Mono.fromCallable(() -> toModel(Objects.requireNonNull(dto, "dto")));
    }

    /** Defers transport conversion until subscription; a null input fails and a null conversion result completes empty. */
    default Mono<D> toDtoMono(M model) {
        return Mono.fromCallable(() -> toDto(Objects.requireNonNull(model, "model")));
    }
}
