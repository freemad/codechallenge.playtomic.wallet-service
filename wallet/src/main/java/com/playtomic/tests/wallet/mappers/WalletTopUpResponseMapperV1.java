package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.wallet.dtos.WalletTopUpResponseDtoV1;
import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;

import static com.playtomic.tests.wallet.enums.WalletOperationStatus.WALLET_OPERATION_STATUS_SUCCEEDED;

/** Maps wallet top up response data across the version-one transport boundary. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletTopUpResponseMapperV1 {
    /** Maps the supplied value to its transport representation. */
    @Mapping(target = "operationId", source = "id")
    @Mapping(target = "status", source = "status", qualifiedByName = "statusName")
    @Mapping(target = "resultingBalance", source = ".", qualifiedByName = "creditedBalance")
    WalletTopUpResponseDtoV1 toDto(WalletOperationModel model);

    /** Returns the external operation status name used by the top-up response. */
    @Named("statusName")
    default String statusName(WalletOperationStatus status) {
        return status.getName();
    }

    /** Returns the recorded resulting balance for a successful credit, otherwise null. */
    @Named("creditedBalance")
    default BigDecimal creditedBalance(WalletOperationModel model) {
        return model.getStatus() == WALLET_OPERATION_STATUS_SUCCEEDED ? model.getResultingBalance() : null;
    }
}