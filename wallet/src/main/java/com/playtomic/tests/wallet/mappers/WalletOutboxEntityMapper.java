package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import com.playtomic.tests.wallet.enums.WalletEventType;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletOutboxEntityMapper extends IEntityToModelMapper<WalletOutboxEntity, WalletOutboxModel, String> {
    WalletOutboxModel toModel(WalletOutboxEntity entity);

    @Mapping(target = "newRecord", ignore = true)
    WalletOutboxEntity toEntity(WalletOutboxModel model);

    default WalletEventType toEventType(String wireValue) {
        return WalletEventType.fromWireValue(wireValue);
    }

    default String toWireValue(WalletEventType eventType) {
        return eventType.getWireValue();
    }
}