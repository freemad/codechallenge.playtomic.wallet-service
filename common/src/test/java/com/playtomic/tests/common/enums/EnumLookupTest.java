package com.playtomic.tests.common.enums;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.EnumLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnumLookupTest {
    private enum State { UNSPECIFIED, ACTIVE, ERROR }

    @Test
    void lookupReturnsFirstMatchWithoutRejectingKnownSentinels() {
        assertSame(State.UNSPECIFIED, EnumLookup.find(State.values(), state -> true, "missing"));
        assertSame(State.ACTIVE, EnumLookup.find(State.values(), state -> state == State.ACTIVE, "missing"));
        assertIllegalArgument("missing", () -> EnumLookup.find(State.values(), state -> false, "missing"));
    }

    @Test
    void acceptsOperationalValue() {
        assertSame(State.ACTIVE, EnumLookup.requireOperational(State.ACTIVE, State.UNSPECIFIED, State.ERROR, "invalid"));
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = State.class, names = {"UNSPECIFIED", "ERROR"})
    void rejectsNullAndSentinels(State state) {
        assertIllegalArgument("invalid", () -> EnumLookup.requireOperational(state, State.UNSPECIFIED, State.ERROR, "invalid"));
    }

    private static void assertIllegalArgument(String message, Executable operation) {
        var exception = assertThrows(BusinessException.class, operation);
        assertSame(CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, exception.getErrorCode());
        assertEquals(message, exception.getAdditionalInfo());
    }
}