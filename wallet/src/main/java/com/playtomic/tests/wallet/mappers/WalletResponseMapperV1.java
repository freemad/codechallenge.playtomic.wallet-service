package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IModelToDtoMapper;
import com.playtomic.tests.wallet.dtos.WalletResponseDtoV1;
import com.playtomic.tests.wallet.models.WalletModel;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Maps wallet response data across the version-one transport boundary. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletResponseMapperV1 extends IModelToDtoMapper<WalletModel, WalletResponseDtoV1> {
    /** Maps the supplied value to its transport representation. */
    @Override
    WalletResponseDtoV1 toDto(WalletModel model);

    /** Maps the supplied representation to its application model. */
    @Override
    WalletModel toModel(WalletResponseDtoV1 dto);
}
