package com.playtomic.tests.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Shared keys used when assembling responses and error details. */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class ResponseParam {
    public static final String ERROR_PARAMS_STACK = "stack";
    public static final String ERROR_PARAMS_INFO = "info";
    public static final String ERROR_PARAMS_MESSAGE = "message";

    public static final String ERROR_ENTITY_NOT_FOUND_MESSAGE = "entity not found";
}
