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

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResponseMappingUtil {

    public static <S, D extends IDto> ResponseDtoV1 toSingleResponseDtoV1(
            Outcome<S, Error> outcome,
            String correlationId,
            Function<S, D> mapperFunction) {
        return toResponse(outcome, correlationId, mapperFunction);
    }

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

    private static <S> ResponseDtoV1 toResponse(
            Outcome<S, Error> outcome, String correlationId, Function<S, ? extends IDto> mapper) {
        ResponseDtoV1 responseDto = new ResponseDtoV1()
                .setCid(correlationId)
                .setMetadata(new HashMap<>());

        if (outcome.getError() != null) {
            return responseDto.setError(mapError(outcome.getError()));
        }

        if (outcome.getResponse() != null) {
            responseDto.setResult(mapper.apply(outcome.getResponse()));
        }

        return responseDto;
    }

    private static ErrorDtoV1 mapError(Error sourceError) {
        return new ErrorDtoV1()
                .setCode(sourceError.getCode() != null ? sourceError.getCode().getCode() : 0)
                .setMessage(sourceError.getMessage());
    }
}
