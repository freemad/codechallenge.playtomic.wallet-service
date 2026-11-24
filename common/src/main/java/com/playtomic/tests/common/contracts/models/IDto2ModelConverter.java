package com.playtomic.tests.common.contracts.models;


import com.playtomic.tests.common.contracts.dtos.IDto;

public interface IDto2ModelConverter<M extends IModel, D extends IDto> {

    M toModel(D dto);
}
