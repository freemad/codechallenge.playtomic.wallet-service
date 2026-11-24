package com.playtomic.tests.common.contracts.mappers;

import com.playtomic.tests.common.contracts.dtos.IDto;

public interface IMessageToDtoMapper<M, D extends IDto> {
    M toMessage(D dto);

    D toDto(M message);
}
