package com.playtomic.tests.common.contracts.models;

import java.io.Serializable;

public interface IOutcome<S, E>
        extends Serializable {
    S getResponse();

    Class<S> getResponseClass();

    E getError();

    Class<E> getErrorClass();

    default boolean isSuccess() {
        return getError() == null;
    }
}