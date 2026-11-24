package com.playtomic.tests.common.models;

import com.playtomic.tests.common.contracts.models.IOutcome;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class Outcome<S, E extends Error>
        implements IOutcome<S, E> {

    @Serial
    private static final long serialVersionUID = 4498404391301878295L;

    private S response;
    private Class<S> responseClass;
    private E error;
    private Class<E> errorClass;

    /**
     * Checks if this outcome represents a failed execution.
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Named convenience method alias to match strategy mappings (.getData())
     */
    public S getData() {
        return this.response;
    }

    // =========================================================================
    // 🏭 STATIC FACTORY METHODS (Fixed Type Inference & Dynamic Class Binding)
    // =========================================================================

    @SuppressWarnings("unchecked")
    public static <S, E extends Error> Outcome<S, E> success(S response) {
        assert response != null;
        return success(response, (Class<S>) response.getClass());
    }

    public static <S, E extends Error> Outcome<S, E> success(S response, Class<S> responseClass) {
        return new Outcome<S, E>()
                .setResponse(response)
                .setResponseClass(responseClass);
    }

    @SuppressWarnings("unchecked")
    public static <S, E extends Error> Outcome<S, E> failure(E error) {
        assert error != null;
        return failure(error, (Class<E>) error.getClass());
    }

    public static <S, E extends Error> Outcome<S, E> failure(E error, Class<E> errorClass) {
        return new Outcome<S, E>()
                .setError(error)
                .setErrorClass(errorClass);
    }
}