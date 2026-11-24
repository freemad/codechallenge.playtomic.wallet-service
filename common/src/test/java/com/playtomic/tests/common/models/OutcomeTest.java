package com.playtomic.tests.common.models;

import com.playtomic.tests.common.contracts.models.IOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeTest {
    @Test
    void successFactoryRetainsResponseAndItsType() {
        var outcome = Outcome.<String, Error>success("value");

        assertSame(outcome.getResponse(), outcome.getData());
        assertSame(String.class, outcome.getResponseClass());
        assertNull(outcome.getError());
        assertTrue(outcome.isSuccess());
        assertFalse(outcome.isFailure());
    }

    @Test
    void failureFactoryRetainsLocalErrorAndItsType() {
        var error = new Error().setMessage("invalid");
        var outcome = Outcome.<String, Error>failure(error);

        assertSame(error, outcome.getError());
        assertSame(Error.class, outcome.getErrorClass());
        assertNull(outcome.getResponse());
        assertFalse(outcome.isSuccess());
        assertTrue(outcome.isFailure());
    }

    @Test
    void successAndFailureFollowErrorPresenceEvenWhenResponseIsMissingOrPresent() {
        var outcome = new Outcome<String, Error>();
        IOutcome<String, Error> contract = outcome;

        assertTrue(contract.isSuccess());
        assertFalse(outcome.isFailure());

        outcome.setResponse("value").setError(new Error());
        assertFalse(contract.isSuccess());
        assertTrue(outcome.isFailure());

        outcome.setError(null);
        assertTrue(contract.isSuccess());
        assertFalse(outcome.isFailure());
    }
}
