package com.playtomic.tests.common.contracts.dtos;

import java.time.OffsetDateTime;
import java.util.Map;

/** Describes a correlated response carrying a result or an error. */
public interface IResponseDto
        extends IDto {
    /** Returns the response correlation identifier. */
    String getCid();

    /** Reports whether the result represents success rather than an error. */
    boolean isSuccess();

    /** Returns the timestamp associated with this record. */
    default String getTimestamp() {
        return String.valueOf(OffsetDateTime.now());
    }

    /** Returns the response result payload. */
    IDto getResult();

    /** Returns the structured failure associated with the result. */
    IErrorDto getError();

    /** Returns the additional response metadata. */
    Map<String, String> getMetadata();
}