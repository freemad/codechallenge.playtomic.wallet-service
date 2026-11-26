package com.playtomic.tests.wallet.controllers;

import com.playtomic.tests.common.contracts.dtos.IDto;
import com.playtomic.tests.common.dtos.ResponseDtoV1;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.utils.ResponseMappingUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WalletHttpResponses {
    static <S, D extends IDto> ResponseEntity<ResponseDtoV1> toResponse(
            Outcome<S, Error> outcome, String correlationId, Function<S, D> mapper,
            HttpStatus status, CacheControl cacheControl) {
        var response = ResponseMappingUtil.toSingleResponseDtoV1(outcome, correlationId, mapper);
        if (status == HttpStatus.INTERNAL_SERVER_ERROR && response.getError() != null) {
            response.getError().setMessage(status.getReasonPhrase());
        }
        return toResponse(response, status, cacheControl);
    }

    static ResponseEntity<ResponseDtoV1> toResponse(IDto result, CacheControl cacheControl) {
        return toResponse(new ResponseDtoV1().setResult(result), HttpStatus.OK, cacheControl);
    }

    private static ResponseEntity<ResponseDtoV1> toResponse(
            ResponseDtoV1 response, HttpStatus status, CacheControl cacheControl) {
        return ResponseEntity.status(status).cacheControl(cacheControl).body(response);
    }
}