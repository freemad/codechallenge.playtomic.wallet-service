package com.playtomic.tests.common.contracts.models;

import java.io.Serializable;

/** Describes the successful response or error produced by an operation. */
public interface IOutcome<S, E>
        extends Serializable {
    /** Returns the successful response payload. */
    S getResponse();

    /** Returns the declared class of the successful response. */
    Class<S> getResponseClass();

    /** Returns the structured failure associated with the result. */
    E getError();

    /** Returns the declared class of the error payload. */
    Class<E> getErrorClass();

    /** Reports whether the result represents success rather than an error. */
    default boolean isSuccess() {
        return getError() == null;
    }
}