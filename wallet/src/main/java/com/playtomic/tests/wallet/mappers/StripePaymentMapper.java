package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.wallet.dtos.StripeDtos.ChargeReceiptDto;
import com.playtomic.tests.wallet.dtos.StripeDtos.ChargeRequestDto;
import com.playtomic.tests.wallet.dtos.StripeDtos.RefundRequestDto;
import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import com.playtomic.tests.wallet.models.PaymentRefundModel;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Maps stripe payment data between application and transport representations. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StripePaymentMapper {
    /** Maps the supplied value to its transport representation. */
    ChargeRequestDto toDto(CardChargeModel model);

    /** Maps the supplied value to its transport representation. */
    RefundRequestDto toDto(PaymentRefundModel model);

    /** Maps the supplied representation to its application model. */
    PaymentReceiptModel toModel(ChargeReceiptDto dto);
}

