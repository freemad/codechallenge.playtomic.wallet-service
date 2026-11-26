package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import com.playtomic.tests.wallet.enums.WalletEventType;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Maps wallet outbox records between MongoDB and application representations. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletOutboxEntityMapper extends IEntityToModelMapper<WalletOutboxEntity, WalletOutboxModel, String> {
    /** Maps the supplied representation to its application model. */
    WalletOutboxModel toModel(WalletOutboxEntity entity);

    /** Maps the application model to its persistence representation. */
    @Mapping(target = "newRecord", ignore = true)
    WalletOutboxEntity toEntity(WalletOutboxModel model);

    /** Resolves the stored event value to the corresponding event type. */
    default WalletEventType toEventType(int value) {
        return WalletEventType.ofValue(value);
    }

    /** Returns the storage representation of the wallet event type. */
    default int toEventTypeValue(WalletEventType eventType) {
        return eventType.getValue();
    }
}