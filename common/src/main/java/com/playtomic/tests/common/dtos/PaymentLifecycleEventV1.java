package com.playtomic.tests.common.dtos;

import com.playtomic.tests.common.contracts.dtos.IDto;
import com.playtomic.tests.common.enums.PaymentReviewOperation;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/** Compile-time placeholder; not a published payment event. */
@Data
@Accessors(chain = true)
public class PaymentLifecycleEventV1 implements IDto {
    private final String type = "payment-lifecycle";
    private final String version = "1";
    private Disposition disposition;

    @Data
    @Accessors(chain = true)
    public static class Disposition {
        private String tenantBizId;
        private UUID tenantId;
        private UUID transactionId;
        private UUID evaluationId;
        private Long revision;
        private PaymentReviewOperation action;
        private UUID actionId;
        private Long issuedAtEpochMs;
    }
}
