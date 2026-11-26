package com.playtomic.tests.common.utils;

import com.playtomic.tests.common.constants.ResponseParam;
import com.playtomic.tests.common.contracts.mappers.IErrorModelMapperV1;
import com.playtomic.tests.common.contracts.mappers.IModelToDtoMapper;
import com.playtomic.tests.common.dtos.ErrorDtoV1;
import com.playtomic.tests.common.dtos.ResponseDtoV1;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Map;

/** Converts business exceptions to responses while controlling diagnostic disclosure. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExceptionUtil {
    /** Maps a business failure into an error response using the selected diagnostic detail level. */
    public static ResponseDtoV1 getBizExceptionResponseDtoV1(BusinessException businessException, IErrorModelMapperV1 errorMapper, boolean debugMode) {
        var errorParams = renderErrorParams(businessException, debugMode);
        return new ResponseDtoV1()
                .setResult(null)
                .setError(errorMapper.toDto(businessException.toError(errorParams)));
    }

    /** Selects which diagnostic details may appear in the response for the given debug setting. */
    public static Map<String, String> renderErrorParams(BusinessException bex, boolean debugMode) {
        return debugMode
                ? Map.of(
                    ResponseParam.ERROR_PARAMS_MESSAGE, bex.getMessage(),
                    ResponseParam.ERROR_PARAMS_INFO, bex.getAdditionalInfo(),
                    ResponseParam.ERROR_PARAMS_STACK, Arrays.toString(bex.getStackTrace()))
                : Map.of(
                    ResponseParam.ERROR_PARAMS_MESSAGE, bex.getErrorCode().getMessage(),
                    ResponseParam.ERROR_PARAMS_INFO, bex.getAdditionalInfo());
    }
}
