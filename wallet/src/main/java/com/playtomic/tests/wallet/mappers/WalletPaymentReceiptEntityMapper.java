package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IEntityToModelMapper;
import com.playtomic.tests.wallet.entities.WalletPaymentReceiptEntity;
import com.playtomic.tests.wallet.models.WalletPaymentReceiptModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Maps wallet payment receipt records between MongoDB and application representations. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletPaymentReceiptEntityMapper extends IEntityToModelMapper<WalletPaymentReceiptEntity, WalletPaymentReceiptModel, String> {
    /** Maps the supplied representation to its application model. */
    WalletPaymentReceiptModel toModel(WalletPaymentReceiptEntity entity);

    /** Maps the application model to its persistence representation. */
    @Mapping(target = "newRecord", ignore = true)
    WalletPaymentReceiptEntity toEntity(WalletPaymentReceiptModel model);
}
