package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.models.WalletModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletEntityMapper extends IEntityToModelMapper<WalletEntity, WalletModel, String> {
    @Override
    WalletModel toModel(WalletEntity entity);

    @Override
    @Mapping(target = "newRecord", ignore = true)
    WalletEntity toEntity(WalletModel model);
}
