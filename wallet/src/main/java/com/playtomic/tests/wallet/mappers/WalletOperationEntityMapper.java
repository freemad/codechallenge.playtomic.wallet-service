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

/** Maps wallet operation records between MongoDB and application representations. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletOperationEntityMapper extends IEntityToModelMapper<WalletOperationEntity, WalletOperationModel, String> {
    /** Maps the supplied representation to its application model. */
    WalletOperationModel toModel(WalletOperationEntity entity);

    /** Maps the application model to its persistence representation. */
    @Mapping(target = "newRecord", ignore = true)
    WalletOperationEntity toEntity(WalletOperationModel model);

    /** Converts a persisted operation type to its application enum. */
    @EnumMapping(nameTransformationStrategy = MappingConstants.PREFIX_TRANSFORMATION, configuration = "WALLET_OPERATION_TYPE_")
    @ValueMapping(source = "UNSPECIFIED", target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "ERROR", target = MappingConstants.THROW_EXCEPTION)
    WalletOperationType toModelType(WalletOperationMongoType type);

    /** Converts an application operation type to its persistence enum. */
    @EnumMapping(nameTransformationStrategy = MappingConstants.STRIP_PREFIX_TRANSFORMATION, configuration = "WALLET_OPERATION_TYPE_")
    @ValueMapping(source = "WALLET_OPERATION_TYPE_UNSPECIFIED", target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "WALLET_OPERATION_TYPE_ERROR", target = MappingConstants.THROW_EXCEPTION)
    WalletOperationMongoType toEntityType(WalletOperationType type);

    /** Converts a persisted operation status to its application enum. */
    @EnumMapping(nameTransformationStrategy = MappingConstants.PREFIX_TRANSFORMATION, configuration = "WALLET_OPERATION_STATUS_")
    @ValueMapping(source = "UNSPECIFIED", target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "ERROR", target = MappingConstants.THROW_EXCEPTION)
    WalletOperationStatus toModelStatus(WalletOperationMongoStatus status);

    /** Converts an application operation status to its persistence enum. */
    @EnumMapping(nameTransformationStrategy = MappingConstants.STRIP_PREFIX_TRANSFORMATION, configuration = "WALLET_OPERATION_STATUS_")
    @ValueMapping(source = "WALLET_OPERATION_STATUS_UNSPECIFIED", target = MappingConstants.THROW_EXCEPTION)
    @ValueMapping(source = "WALLET_OPERATION_STATUS_ERROR", target = MappingConstants.THROW_EXCEPTION)
    WalletOperationMongoStatus toEntityStatus(WalletOperationStatus status);
}