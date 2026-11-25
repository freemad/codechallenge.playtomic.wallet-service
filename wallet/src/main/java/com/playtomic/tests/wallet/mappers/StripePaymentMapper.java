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

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StripePaymentMapper {
    ChargeRequestDto toDto(CardChargeModel model);

    RefundRequestDto toDto(PaymentRefundModel model);

    PaymentReceiptModel toModel(ChargeReceiptDto dto);
}

