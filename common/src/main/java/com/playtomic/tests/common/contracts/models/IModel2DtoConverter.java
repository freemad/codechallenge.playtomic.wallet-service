package com.playtomic.tests.common.contracts.models;


import com.playtomic.tests.common.contracts.dtos.IDto;

public interface IModel2DtoConverter<M extends IModel, D extends IDto> {

    D toDto(M model);
}
