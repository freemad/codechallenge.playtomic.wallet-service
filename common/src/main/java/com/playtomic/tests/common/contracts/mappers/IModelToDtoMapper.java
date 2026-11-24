package com.playtomic.tests.common.contracts.mappers;

import com.playtomic.tests.common.contracts.dtos.IDto;

public interface IModelToDtoMapper<M, D extends IDto> {
    M toModel(D dto);

    D toDto(M model);
}
