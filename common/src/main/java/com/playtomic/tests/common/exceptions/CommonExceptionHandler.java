package com.playtomic.tests.common.exceptions;

import com.playtomic.tests.common.contracts.exceptions.IErrorCode;
import com.playtomic.tests.common.dtos.ResponseDtoV1;
import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.common.utils.ResponseMappingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

/** Converts exceptions escaping REST controllers into shared error responses. */
@Slf4j
@RestControllerAdvice
public class CommonExceptionHandler {

    /** Converts an exception escaping a controller into the shared HTTP error response. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDtoV1> handleException(Exception exception, ServerWebExchange exchange) throws Exception {
        if (ExceptionNormalizer.mustPropagate(exception) || exchange.getResponse().isCommitted()) {
            throw exception;
        }

        String correlationId = exchange.getRequest().getId();
        if (exception instanceof ErrorResponse httpError) {
            var status = httpError.getStatusCode();
            if (!status.isError()) {
                throw exception;
            }
            var knownStatus = HttpStatus.resolve(status.value());
            var code = status.is4xxClientError()
                    ? CommonErrorCode.PROTOCOL_EXCEPTION : CommonErrorCode.INTERNAL_SERVICE_EXCEPTION;
            String message = knownStatus != null ? knownStatus.getReasonPhrase() : code.getDescription();
            if (status.is5xxServerError()) {
                logFailure(exception, correlationId);
            }
            return ResponseEntity.status(status)
                    .headers(httpError.getHeaders())
                    .body(errorResponse(code, message, correlationId));
        }

        logFailure(exception, correlationId);
        var normalized = ExceptionNormalizer.normalize(exception);
        var code = normalized instanceof BusinessException businessException
                ? businessException.getErrorCode() : CommonErrorCode.INTERNAL_SERVICE_EXCEPTION;
        return ResponseEntity.internalServerError()
                .body(errorResponse(code, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), correlationId));
    }

    private static ResponseDtoV1 errorResponse(IErrorCode code, String message, String correlationId) {
        return ResponseMappingUtil.toErrorResponseDtoV1(new Error().setCode(code).setMessage(message), correlationId);
    }

    private static void logFailure(Exception exception, String correlationId) {
        log.error("Controller failure (cid={}, exceptionType={})", correlationId, exception.getClass().getSimpleName());
    }
}