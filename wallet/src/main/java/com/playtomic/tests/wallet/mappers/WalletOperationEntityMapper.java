package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletOperationEntityMapper extends IEntityToModelMapper<WalletOperationEntity, WalletOperationModel, String> {
    WalletOperationModel toModel(WalletOperationEntity entity);

    @Mapping(target = "newRecord", ignore = true)
    WalletOperationEntity toEntity(WalletOperationModel model);
}
