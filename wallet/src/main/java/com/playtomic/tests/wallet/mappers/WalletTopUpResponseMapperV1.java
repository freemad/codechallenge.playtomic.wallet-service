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

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletTopUpResponseMapperV1 {
    @Mapping(target = "operationId", source = "id")
    @Mapping(target = "status", source = "status", qualifiedByName = "statusName")
    @Mapping(target = "resultingBalance", source = ".", qualifiedByName = "creditedBalance")
    WalletTopUpResponseDtoV1 toDto(WalletOperationModel model);

    @Named("statusName")
    default String statusName(WalletOperationStatus status) {
        return status.getName();
    }

    @Named("creditedBalance")
    default BigDecimal creditedBalance(WalletOperationModel model) {
        return model.getStatus() == WALLET_OPERATION_STATUS_SUCCEEDED ? model.getResultingBalance() : null;
    }
}