package com.playtomic.tests.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorCode {
    public static final int PLAYTOMIC_TESTS_ERROR_CODE_PREFIX = 11000;
    public static final int WALLET_ERROR_CODE_PREFIX = PLAYTOMIC_TESTS_ERROR_CODE_PREFIX + 1000;
    /******* COMMON ERROR CODE PREFIXES *******/
    public static final int INITIAL_ERROR_CODE_COMMON = PLAYTOMIC_TESTS_ERROR_CODE_PREFIX + 100;
    /******* WALLET ERROR CODE PREFIXES *******/
    public static final int INITIAL_ERROR_CODE_WALLET = WALLET_ERROR_CODE_PREFIX;
}