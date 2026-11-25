package com.playtomic.tests.common.contracts.mappers;


import com.playtomic.tests.common.contracts.exceptions.IErrorCode;
import com.playtomic.tests.common.dtos.ErrorDtoV1;
import com.playtomic.tests.common.models.Error;

public interface IErrorModelMapperV1
        extends IModelToDtoMapper<Error, ErrorDtoV1> {
    IErrorCode getErrorCode(Integer code);

    default Error toModel(ErrorDtoV1 dto) {
        return new Error()
                .setCode(getErrorCode(dto.getCode()))
                .setMessage(dto.getMessage())
                .setParams(dto.getParams());
    }

    default ErrorDtoV1 toDto(Error model) {
        return new ErrorDtoV1()
                .setCode(model.getCode().getCode())
                .setMessage(model.getMessage())
                .setParams(model.getParams());
    }
}