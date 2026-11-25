package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import com.playtomic.tests.wallet.models.WalletJobLeaseModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletJobLeaseEntityMapper extends IEntityToModelMapper<WalletJobLeaseEntity, WalletJobLeaseModel, String> {
    WalletJobLeaseModel toModel(WalletJobLeaseEntity entity);

    @Mapping(target = "newRecord", ignore = true)
    WalletJobLeaseEntity toEntity(WalletJobLeaseModel model);
}
