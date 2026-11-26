package com.playtomic.tests.common.contracts.mappers;


import com.playtomic.tests.common.contracts.exceptions.IErrorCode;
import com.playtomic.tests.common.dtos.ErrorDtoV1;
import com.playtomic.tests.common.models.Error;

/** Maps error model data across the version-one transport boundary. */
public interface IErrorModelMapperV1
        extends IModelToDtoMapper<Error, ErrorDtoV1> {
    /** Resolves the numeric transport code to its application error classification. */
    IErrorCode getErrorCode(Integer code);

    /** Maps the supplied representation to its application model. */
    default Error toModel(ErrorDtoV1 dto) {
        return new Error()
                .setCode(getErrorCode(dto.getCode()))
                .setMessage(dto.getMessage())
                .setParams(dto.getParams());
    }

    /** Maps the supplied value to its transport representation. */
    default ErrorDtoV1 toDto(Error model) {
        return new ErrorDtoV1()
                .setCode(model.getCode().getCode())
                .setMessage(model.getMessage())
                .setParams(model.getParams());
    }
}