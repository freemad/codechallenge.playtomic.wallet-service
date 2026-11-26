package com.playtomic.tests.common.contracts.mappers;

import com.playtomic.tests.common.contracts.dtos.IDto;

/** Maps between messaging and transport representations. */
public interface IMessageToDtoMapper<M, D extends IDto> {
    /** Maps the transport object to its message representation. */
    M toMessage(D dto);

    /** Maps the supplied value to its transport representation. */
    D toDto(M message);
}
