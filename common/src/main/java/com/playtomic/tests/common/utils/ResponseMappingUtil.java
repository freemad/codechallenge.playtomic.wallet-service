package com.playtomic.tests.common.utils;

import com.playtomic.tests.common.contracts.dtos.IDto;
import com.playtomic.tests.common.dtos.CollectionResultDtoV1;
import com.playtomic.tests.common.dtos.ErrorDtoV1;
import com.playtomic.tests.common.dtos.ResponseDtoV1;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds version-one response envelopes from service outcomes. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResponseMappingUtil {

    /** Wraps a mapped result or the outcome's error in a correlated response. */
    public static <S, D extends IDto> ResponseDtoV1 toSingleResponseDtoV1(
            Outcome<S, Error> outcome,
            String correlationId,
            Function<S, D> mapperFunction) {
        return toResponse(outcome, correlationId, mapperFunction);
    }

    /** Wraps mapped collection elements or the outcome's error in a correlated response. */
    public static <S, D extends IDto> ResponseDtoV1 toListResponseDtoV1(
            Outcome<List<S>, Error> outcome,
            String correlationId,
            Function<S, D> elementMapperFunction) {
        return toResponse(outcome, correlationId, items -> {
            List<D> mappedItems = items.stream().map(elementMapperFunction).collect(Collectors.toList());
            if (mappedItems.isEmpty()) {
                return new CollectionResultDtoV1<>(null, null, mappedItems);
            }
            IDto first = mappedItems.getFirst();
            return new CollectionResultDtoV1<>(first.getType(), first.getVersion(), mappedItems);
        });
    }

    /** Builds a correlated error response without a result payload. */
    public static ResponseDtoV1 toErrorResponseDtoV1(Error error, String correlationId) {
        return newResponse(correlationId).setError(mapError(error));
    }

    private static <S> ResponseDtoV1 toResponse(
            Outcome<S, Error> outcome, String correlationId, Function<S, ? extends IDto> mapper) {
        if (outcome.getError() != null) {
            return toErrorResponseDtoV1(outcome.getError(), correlationId);
        }

        ResponseDtoV1 responseDto = newResponse(correlationId);
        if (outcome.getResponse() != null) {
            return responseDto.setResult(mapper.apply(outcome.getResponse()));
        }

        return responseDto;
    }

    private static ResponseDtoV1 newResponse(String correlationId) {
        return new ResponseDtoV1().setCid(correlationId).setMetadata(new HashMap<>());
    }

    private static ErrorDtoV1 mapError(Error sourceError) {
        return new ErrorDtoV1()
                .setCode(sourceError.getCode() != null ? sourceError.getCode().getCode() : 0)
                .setMessage(sourceError.getMessage());
    }
}