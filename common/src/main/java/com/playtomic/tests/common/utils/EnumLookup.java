package com.playtomic.tests.common.utils;

import com.playtomic.tests.common.exceptions.BusinessException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.function.Predicate;

import static com.playtomic.tests.common.enums.CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION;

/** Resolves enum values and rejects non-operational sentinels at application boundaries. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EnumLookup {
    /** Returns the first matching enum member or raises a business error when none match. */
    public static <E extends Enum<E>> E find(E[] values, Predicate<E> matches, String message) {
        return Arrays.stream(values).filter(matches).findFirst()
                .orElseThrow(() -> new BusinessException(ILLEGAL_ARGUMENT_EXCEPTION, message));
    }

    /** Rejects null and the designated unspecified and error sentinels. */
    public static <E extends Enum<E>> E requireOperational(E candidate, E unspecified, E error, String message) {
        if (candidate == null || candidate == unspecified || candidate == error) {
            throw new BusinessException(ILLEGAL_ARGUMENT_EXCEPTION, message);
        }
        return candidate;
    }
}