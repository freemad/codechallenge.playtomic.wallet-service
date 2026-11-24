package com.playtomic.tests.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Shared HTTP contract keys without an equivalent Spring {@code HttpHeaders} constant.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpParam {
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
}
