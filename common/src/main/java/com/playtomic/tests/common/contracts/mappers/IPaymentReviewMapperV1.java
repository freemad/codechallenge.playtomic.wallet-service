package com.playtomic.tests.common.contracts.mappers;

import com.playtomic.tests.common.dtos.PaymentReviewDtoV1;
import com.playtomic.tests.common.models.PaymentReviewModel;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IPaymentReviewMapperV1 extends IModelToDtoMapper<PaymentReviewModel, PaymentReviewDtoV1> {
    PaymentReviewModel toModel(PaymentReviewDtoV1 dto);

    PaymentReviewDtoV1 toDto(PaymentReviewModel model);
}