package com.playtomic.tests.common.utils;

import com.playtomic.tests.common.dtos.CollectionResultDtoV1;
import com.playtomic.tests.common.dtos.ErrorDtoV1;
import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ResponseMappingUtilTest {
    @Test
    void mapsSingleResultWithEnvelopeMetadata() {
        var result = new ErrorDtoV1().setMessage("example");
        var outcome = Outcome.<String, Error>success("value");
        var response = ResponseMappingUtil.toSingleResponseDtoV1(outcome, "correlation-id", value -> {
            assertEquals("value", value);
            return result;
        });

        assertTrue(outcome.isSuccess());
        assertFalse(outcome.isFailure());
        assertEquals("correlation-id", response.getCid());
        assertEquals(Map.of(), response.getMetadata());
        assertSame(result, response.getResult());
        assertNull(response.getError());
    }

    @Test
    void keepsListOrderAndEmptyListDefaults() {
        var first = new ErrorDtoV1().setMessage("first");
        var second = new ErrorDtoV1().setMessage("second");
        var outcome = Outcome.<List<String>, Error>success(List.of("first", "second"));
        var response = ResponseMappingUtil.toListResponseDtoV1(outcome, "list", item -> new ErrorDtoV1().setMessage(item));
        var result = assertInstanceOf(CollectionResultDtoV1.class, response.getResult());

        assertEquals("error-list", result.getType());
        assertEquals("1", result.getVersion());
        assertEquals(List.of(first, second), result.getItems());

        var empty = Outcome.<List<ErrorDtoV1>, Error>success(List.of());
        var emptyResponse = ResponseMappingUtil.toListResponseDtoV1(empty, "empty", item -> item);
        var emptyResult = assertInstanceOf(CollectionResultDtoV1.class, emptyResponse.getResult());

        assertEquals("generic-collection-list", emptyResult.getType());
        assertEquals("1", emptyResult.getVersion());
        assertEquals(List.of(), emptyResult.getItems());
    }

    @Test
    void failureTakesPrecedenceWithoutMappingTheResult() {
        var code = CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION;
        var error = new Error().setCode(code).setMessage("invalid").setParams(Map.of("detail", "internal"));
        var outcome = Outcome.<List<String>, Error>failure(error).setResponse(List.of("unused"));
        var response = ResponseMappingUtil.toListResponseDtoV1(outcome, "failed", item -> fail("mapped failed result"));

        assertTrue(outcome.isFailure());
        assertFalse(outcome.isSuccess());
        assertEquals("failed", response.getCid());
        assertEquals(Map.of(), response.getMetadata());
        assertNull(response.getResult());
        assertEquals(code.getCode(), response.getError().getCode());
        assertEquals("invalid", response.getError().getMessage());
        assertNull(response.getError().getParams());
    }

    @Test
    void mapsSingleFailureWithoutAnErrorCodeOrCallingTheResultMapper() {
        var outcome = Outcome.<String, Error>failure(new Error().setMessage("invalid")).setResponse("unused");
        var response = ResponseMappingUtil.toSingleResponseDtoV1(outcome, "failed", item -> fail("mapped failed result"));

        assertNull(response.getResult());
        assertEquals(0, response.getError().getCode());
        assertEquals("invalid", response.getError().getMessage());
    }

    @Test
    void missingResultsLeaveBothEnvelopesEmptyWithoutCallingMappers() {
        var single = ResponseMappingUtil.toSingleResponseDtoV1(new Outcome<String, Error>(), "single",
                item -> fail("mapped missing result"));
        var list = ResponseMappingUtil.toListResponseDtoV1(new Outcome<List<String>, Error>(), "list",
                item -> fail("mapped missing list"));

        assertEquals("single", single.getCid());
        assertNull(single.getResult());
        assertNull(single.getError());
        assertEquals("list", list.getCid());
        assertNull(list.getResult());
        assertNull(list.getError());
    }
}
