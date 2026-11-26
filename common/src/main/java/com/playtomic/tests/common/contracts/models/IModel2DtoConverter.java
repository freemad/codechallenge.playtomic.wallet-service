package com.playtomic.tests.common.contracts.models;


import com.playtomic.tests.common.contracts.dtos.IDto;

/** Projects an application model into its transport representation. */
public interface IModel2DtoConverter<M extends IModel, D extends IDto> {

    /** Maps the supplied value to its transport representation. */
    D toDto(M model);
}
