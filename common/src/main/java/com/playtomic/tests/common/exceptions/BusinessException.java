package com.playtomic.tests.common.exceptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.POJONode;
import com.playtomic.tests.common.contracts.exceptions.IBusinessException;
import com.playtomic.tests.common.contracts.exceptions.IErrorCode;
import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.models.Error;
import lombok.Getter;

import java.io.Serial;
import java.util.Map;

/** Reports an application error code with optional diagnostic context and cause. */
@Getter
public class BusinessException
        extends RuntimeException
        implements IBusinessException {
    @Serial
    private static final long serialVersionUID = -4336964182272706621L;
    private static final String BUSINESS_EXCEPTION_KEY = "BusinessException";
    private final IErrorCode errorCode;
    private final String additionalInfo;
    private final Throwable cause;
    private final boolean enableSuppression;
    private final boolean writableStackTrace;
    private final ObjectMapper mapper = new JsonMapper();

    /** Creates a business failure classified by the supplied application error code. */
    public BusinessException(IErrorCode errorCode) {
        this(errorCode, "", null, false, true);
    }

    /** Creates a business failure classified by the supplied application error code. */
    public BusinessException(IErrorCode errorCode, String additionalInfo) {
        this(errorCode, additionalInfo, null, false, true);
    }

    /** Creates a business failure classified by the supplied application error code. */
    public BusinessException(IErrorCode errorCode, String additionalInfo, Throwable cause) {
        this(errorCode, additionalInfo, cause, false, true);
    }

    /** Creates a business failure classified by the supplied application error code. */
    public BusinessException(IErrorCode errorCode, String additionalInfo, boolean enableSuppression, boolean writableStackTrace) {
        this(errorCode, additionalInfo, null, enableSuppression, writableStackTrace);
    }

    protected BusinessException(
            IErrorCode errorCode,
            String additionalInfo,
            Throwable cause,
            boolean enableSuppression,
            boolean writableStackTrace
    ) {
        super(errorCode.getMessage(), cause, enableSuppression, writableStackTrace);
        this.errorCode = errorCode;
        this.additionalInfo = additionalInfo;
        this.cause = cause;
        this.enableSuppression = enableSuppression;
        this.writableStackTrace = writableStackTrace;
    }

    /** Wraps this exception in a Jackson POJO node. */
    public POJONode toJson() {
        return new POJONode(this);
    }

    /**
     * Serializes this error, retaining serialization failures as structured business exceptions.
     *
     * @return JSON representation
     * @throws BusinessException if Jackson cannot serialize the exception
     */
    public String toJsonString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException error) {
            throw new BusinessException(CommonErrorCode.ILLEGAL_FORMAT_EXCEPTION,
                    "Unable to serialize business exception", error);
        }
    }

    /** Returns the deepest cause, or null if no cause was attached. */
    public Throwable getRootCause() {
        var rootCause = getCause();
        while (rootCause != null && rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    /** Builds an application error with the exception's code and generated message. */
    public Error toError(Map<String, String> params) {
        return new Error()
                .setCode(errorCode)
                .setMessage(generateErrorMessage())
                .setParams(params);
    }

    /** Builds an application error with the exception's code and generated message. */
    public Error toError() {
        return this.toError(null);
    }

    private String generateErrorMessage() {
        return !getAdditionalInfo().isEmpty()
                ? getMessage() + ": " + getAdditionalInfo()
                : getMessage();
    }
}
