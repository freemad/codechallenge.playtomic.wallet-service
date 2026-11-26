package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.models.WalletModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Maps wallet records between MongoDB and application representations. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletEntityMapper extends IEntityToModelMapper<WalletEntity, WalletModel, String> {
    /** Maps the supplied representation to its application model. */
    @Override
    WalletModel toModel(WalletEntity entity);

    /** Maps the application model to its persistence representation. */
    @Override
    @Mapping(target = "newRecord", ignore = true)
    WalletEntity toEntity(WalletModel model);
}
