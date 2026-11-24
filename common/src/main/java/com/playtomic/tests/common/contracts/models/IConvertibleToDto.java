package com.playtomic.tests.common.contracts.models;



import com.playtomic.tests.common.contracts.dtos.IDto;

import java.io.Serializable;

public interface IConvertibleToDto<D extends IDto>
        extends Serializable {

    D toDto();
}
