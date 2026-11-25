package com.playtomic.tests.common.models;

import com.playtomic.tests.common.enums.PaymentReviewOperation;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/** Compile-time placeholder containing only the properties the draft mapper needs. */
@Data
@Accessors(chain = true)
public class PaymentReviewCaseModel {
    private String tenantBizId;
    private UUID tenantId;
    private UUID transactionId;
    private UUID evaluationId;
    private Long paymentRevision;
    private PaymentReviewOperation action;
    private UUID actionId;
    private Long issuedAtEpochMs;
}
