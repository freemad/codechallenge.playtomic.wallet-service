package com.playtomic.tests.common.contracts.mappers;

import com.playtomic.tests.common.dtos.PaymentLifecycleEventV1;
import com.playtomic.tests.common.models.PaymentReviewCaseModel;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(uses = IPaymentReviewMapperV1.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IPaymentReviewCaseMapperV1 extends IModelToDtoMapper<PaymentReviewCaseModel, PaymentLifecycleEventV1> {
    @Mapping(target = "disposition.tenantBizId", source = "tenantBizId")
    @Mapping(target = "disposition.tenantId", source = "tenantId")
    @Mapping(target = "disposition.transactionId", source = "transactionId")
    @Mapping(target = "disposition.evaluationId", source = "evaluationId")
    @Mapping(target = "disposition.revision", source = "paymentRevision")
    @Mapping(target = "disposition.action", source = "action")
    @Mapping(target = "disposition.actionId", source = "actionId")
    @Mapping(target = "disposition.issuedAtEpochMs", source = "issuedAtEpochMs")
    PaymentLifecycleEventV1 toDto(PaymentReviewCaseModel model);

    @InheritInverseConfiguration
    PaymentReviewCaseModel toModel(PaymentLifecycleEventV1 dto);
}