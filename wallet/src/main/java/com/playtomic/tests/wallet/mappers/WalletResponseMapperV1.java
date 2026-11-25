package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IModelToDtoMapper;
import com.playtomic.tests.wallet.dtos.WalletResponseDtoV1;
import com.playtomic.tests.wallet.models.WalletModel;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletResponseMapperV1 extends IModelToDtoMapper<WalletModel, WalletResponseDtoV1> {
    @Override
    WalletResponseDtoV1 toDto(WalletModel model);

    @Override
    WalletModel toModel(WalletResponseDtoV1 dto);
}
