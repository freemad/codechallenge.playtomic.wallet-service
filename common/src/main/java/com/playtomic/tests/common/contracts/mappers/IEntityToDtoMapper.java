package com.playtomic.tests.common.contracts.mappers;


import com.playtomic.tests.common.contracts.dtos.IDto;
import com.playtomic.tests.common.contracts.models.IEntity;

import java.io.Serializable;

public interface IEntityToDtoMapper<T extends IEntity<I>, D extends IDto, I extends Serializable> {
    T toEntity(D dto);

    D toDto(T entity);
}
