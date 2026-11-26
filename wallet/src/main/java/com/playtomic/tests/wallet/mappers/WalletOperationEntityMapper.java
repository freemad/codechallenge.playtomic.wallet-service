package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import com.playtomic.tests.wallet.enums.WalletOperationMongoType;
import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.enums.WalletOperationType;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import org.mapstruct.EnumMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletOperationEntityMapper extends IEntityToModelMapper<WalletOperationEntity, WalletOperationModel, String> {
    WalletOperationModel toModel(WalletOperationEntity entity);

    @Mapping(target = "newRecord", ignore = true)
    WalletOperationEntity toEntity(WalletOperationModel model);

    @EnumMapping(nameTransformationStrategy = MappingConstants.PREFIX_TRANSFORMATION, configuration = "WALLET_OPERATION_TYPE_")
    @ValueMapping(source = "UNSPECIFIED", target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "ERROR", target = MappingConstants.THROW_EXCEPTION)
    WalletOperationType toModelType(WalletOperationMongoType type);

    @EnumMapping(nameTransformationStrategy = MappingConstants.STRIP_PREFIX_TRANSFORMATION, configuration = "WALLET_OPERATION_TYPE_")
    @ValueMapping(source = "WALLET_OPERATION_TYPE_UNSPECIFIED", target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "WALLET_OPERATION_TYPE_ERROR", target = MappingConstants.THROW_EXCEPTION)
    WalletOperationMongoType toEntityType(WalletOperationType type);

    @EnumMapping(nameTransformationStrategy = MappingConstants.PREFIX_TRANSFORMATION, configuration = "WALLET_OPERATION_STATUS_")
    @ValueMapping(source = "UNSPECIFIED", target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "ERROR", target = MappingConstants.THROW_EXCEPTION)
    WalletOperationStatus toModelStatus(WalletOperationMongoStatus status);

    @EnumMapping(nameTransformationStrategy = MappingConstants.STRIP_PREFIX_TRANSFORMATION, configuration = "WALLET_OPERATION_STATUS_")
    @ValueMapping(source = "WALLET_OPERATION_STATUS_UNSPECIFIED", target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "WALLET_OPERATION_STATUS_ERROR", target = MappingConstants.THROW_EXCEPTION)
    WalletOperationMongoStatus toEntityStatus(WalletOperationStatus status);
}