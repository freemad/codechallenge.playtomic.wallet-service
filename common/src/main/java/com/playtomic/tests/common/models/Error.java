package com.playtomic.tests.common.models;

import com.playtomic.tests.common.contracts.exceptions.IError;
import com.playtomic.tests.common.contracts.exceptions.IErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.util.Map;

/** Carries an error code and optional diagnostic details inside the application. */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class Error
        implements IError {
    @Serial
    private static final long serialVersionUID = 1957525281832521351L;

    private IErrorCode code;
    private String message;
    private BusinessException exception;
    private Map<String, String> params;

    /** Creates an error from the supplied exception and diagnostic parameters. */
    public static Error ofException(BusinessException exception, Map<String, String> params) {
        return new Error()
                .setCode(exception.getErrorCode())
                .setMessage(exception.getErrorCode().getMessage())
                .setException(exception)
                .setParams(params);
    }
}