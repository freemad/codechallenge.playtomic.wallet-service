package com.playtomic.tests.common.utils;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import javax.security.sasl.AuthenticationException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.AccessDeniedException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Normalizes application failures while preserving business errors, transport statuses and control flow.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExceptionNormalizer {
    /**
     * Normalizes an application exception using the default internal-service classification.
     *
     * @param error original exception
     * @return normalized business exception or a preserved transport or control-flow signal
     */
    public static Exception normalize(Exception error) {
        return normalize(error, CommonErrorCode.INTERNAL_SERVICE_EXCEPTION, "");
    }

    /**
     * Adapts reactive failures without converting JVM Errors into recoverable business exceptions.
     *
     * @param error original failure
     * @return normalized exception, or the original Error
     */
    public static Throwable normalize(Throwable error) {
        return error instanceof Exception exception ? normalize(exception) : error;
    }

    /**
     * Wraps an application exception once, retaining its cause and a boundary-specific diagnostic.
     *
     * @param error original exception
     * @param fallbackCode code for failures without a more specific classification
     * @param context operation that failed, or an empty string
     * @return business exception, or the unchanged business, transport or control-flow exception
     */
    public static Exception normalize(Exception error, CommonErrorCode fallbackCode, String context) {
        if (error instanceof BusinessException || mustPropagate(error)
                || error instanceof ErrorResponseException || error instanceof WebClientResponseException
                || error instanceof AuthenticationException || error instanceof AccessDeniedException) {
            return error;
        }
        CommonErrorCode code = error instanceof IllegalArgumentException
                ? CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION
                : error instanceof IllegalStateException ? CommonErrorCode.ILLEGAL_STATE_EXCEPTION
                : error instanceof NullPointerException ? CommonErrorCode.NULL_POINTER_EXCEPTION
                : error instanceof DataAccessException ? CommonErrorCode.DATA_RETRIEVE_EXCEPTION
                : error instanceof ConnectException ? CommonErrorCode.CONNECTION_EXCEPTION
                : error instanceof TimeoutException ? CommonErrorCode.TIME_BOX_EXCEPTION
                : error instanceof IOException ? CommonErrorCode.INFRASTRUCTURE_EXCEPTION
                : fallbackCode;
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new BusinessException(code, context.isEmpty() ? detail : context + ": " + detail, error);
    }

    /**
     * Identifies cancellation and interruption signals that must not become application failures.
     *
     * @param error signal to inspect
     * @return whether it represents cancellation or interruption
     */
    public static boolean isControlFlow(Throwable error) {
        return hasCause(error, cause -> cause instanceof CancellationException
                || cause instanceof InterruptedException || Exceptions.isCancel(cause));
    }

    /** Identifies JVM errors and Reactor fatal signals, including wrapped causes. */
    public static boolean isFatal(Throwable error) {
        return hasCause(error, cause -> cause instanceof Error || Exceptions.isFatal(cause));
    }

    /** Returns true for cancellation, interruption or fatal signals anywhere in the cause chain. */
    public static boolean mustPropagate(Throwable error) {
        return isControlFlow(error) || isFatal(error);
    }

    private static boolean hasCause(Throwable error, Predicate<Throwable> matches) {
        if (error == null || matches.test(error)) {
            return error != null;
        }
        if (error.getCause() == null) {
            return false;
        }
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        seen.add(error);
        for (Throwable cause = error.getCause(); cause != null && seen.add(cause); cause = cause.getCause()) {
            if (matches.test(cause)) {
                return true;
            }
        }
        return false;
    }
}