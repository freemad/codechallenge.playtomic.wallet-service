package com.playtomic.tests.common.enums;

import com.playtomic.tests.common.contracts.exceptions.IErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static com.playtomic.tests.common.constants.ErrorCode.INITIAL_ERROR_CODE_COMMON;


@Getter
@AllArgsConstructor
public enum CommonErrorCode
        implements IErrorCode {
    /******* GENERAL EXCEPTION *******/
    GENERAL_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 1, "GENERAL_EXCEPTION", "general exception"),
    /******* INFRA-BASED EXCEPTIONS *******/
    CONNECTION_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 2, "CONNECTION_EXCEPTION", "connection exception"),
    INFRASTRUCTURE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 3, "INFRASTRUCTURE_EXCEPTION", "infrastructure exception"),
    /******* SECURITY-BASED EXCEPTIONS *******/
    AUTHENTICATION_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 10, "AUTHENTICATION_EXCEPTION", "authentication exception"),
    AUTHORIZATION_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 11, "AUTHORIZATION_EXCEPTION", "authorization exception"),
    /******* INPUT EXCEPTIONS *******/
    PROTOCOL_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 20, "PROTOCOL_EXCEPTION", "protocol exception"),
    METHOD_VALIDATION_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 21, "METHOD_VALIDATION_EXCEPTION", "method validation exception"),
    ILLEGAL_FORMAT_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 22, "ILLEGAL_FORMAT_EXCEPTION", "illegal format exception"),
    ILLEGAL_ARGUMENT_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 23, "ILLEGAL_ARGUMENT_EXCEPTION", "illegal argument exception"),
    ILLEGAL_STATE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 24, "ILLEGAL_STATE_EXCEPTION", "illegal state exception"),
    ALREADY_EXISTS_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 25, "ALREADY_EXISTS_EXCEPTION", "already exists exception"),
    DATA_VALIDATION_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 26, "DATA_VALIDATION_EXCEPTION", "data validation exception"),
    BAD_REQUEST_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 27, "BAD_REQUEST_EXCEPTION", "bad request exception"),
    /******* PROCESSING EXCEPTIONS *******/
    ACCESS_VIOLATION_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 30, "ACCESS_VIOLATION_EXCEPTION", "access violation exception"),
    PARSE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 31, "PARSE_EXCEPTION", "parse exception"),
    NULL_POINTER_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 32, "NULL_POINTER_EXCEPTION", "null pointer exception"),
    PROCESSING_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 33, "PROCESSING_EXCEPTION", "processing exception"),
    MEMORY_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 34, "MEMORY_EXCEPTION", "memory exception"),
    ABORTED_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 35, "ABORTED_EXCEPTION", "aborted exception"),
    CONCURRENCY_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 36, "CONCURRENCY_EXCEPTION", "concurrency exception"),
    DATA_RETRIEVE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 37, "DATA_RETRIEVE_EXCEPTION", "data retrieve exception"),
    DATA_PERSISTENCE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 38, "DATA_PERSISTENCE_EXCEPTION", "data persistence exception"),
    NOT_FOUND_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 39, "NOT_FOUND_EXCEPTION", "not found exception"),
    EVENT_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 40, "EVENT_EXCEPTION", "event exception"),
    LIFECYCLE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 41, "LIFECYCLE_EXCEPTION", "lifecycle exception"),
    TIME_BOX_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 42, "TIME_BOX_EXCEPTION", "time-box exception"),
    NOT_IMPLEMENTED_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 43, "NOT_IMPLEMENTED_EXCEPTION", "not implemented exception"),
    INTERNAL_SERVICE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 44, "INTERNAL_SERVICE_EXCEPTION", "internal service exception"),
    GATEWAY_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 45, "GATEWAY_EXCEPTION", "gateway exception"),
    RESOURCE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 46, "RESOURCE_EXCEPTION", "resource exception"),
    SERVICE_UNAVAILABLE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 47, "SERVICE_UNAVAILABLE_EXCEPTION", "service unavailable exception"),
    EXTERNAL_SERVICE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 48, "EXTERNAL_SERVICE_EXCEPTION", "external service exception"),
    REMOTE_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 49, "REMOTE_EXCEPTION", "remote exception"),
    /******* UNKNOWN EXCEPTION *******/
    UNKNOWN_EXCEPTION(INITIAL_ERROR_CODE_COMMON + 99, "UNKNOWN_EXCEPTION", "unknown exception");

    private final int code;
    private final String message;
    private final String description;

    /**
     * Requires a non-null error code; every declared code is valid.
     *
     * @param candidate value required by the operation
     * @return the validated value
     * @throws BusinessException if the value is not operationally valid
     */
    public static CommonErrorCode validate(CommonErrorCode candidate) {
        if (candidate == null) {
            throw invalid("CommonErrorCode requires a non-null operational value");
        }
        return candidate;
    }

    public static CommonErrorCode ofCode(int code) {
        return Arrays.stream(CommonErrorCode.values())
                .filter(ec -> ec.getCode() == code)
                .findFirst()
                .orElseThrow(() -> invalid("unknown CommonErrorCode! code: " + code));
    }

    public static CommonErrorCode ofMessage(String message) {
        return Arrays.stream(CommonErrorCode.values())
                .filter(ec -> ec.getMessage().equals(message))
                .findFirst()
                .orElseThrow(() -> invalid("unknown CommonErrorCode! message: " + message));
    }

    /**
     * Creates the enum-specific business error for invalid parsing or operational input.
     *
     * @param message explanation of the invalid value
     * @return exception carrying the illegal-argument error code
     */
    private static BusinessException invalid(String message) {
        return new BusinessException(ILLEGAL_ARGUMENT_EXCEPTION, message);
    }

}
