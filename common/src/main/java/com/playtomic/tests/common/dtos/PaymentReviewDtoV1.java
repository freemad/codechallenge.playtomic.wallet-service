package com.playtomic.tests.common.dtos;

import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.Getter;

/** Compile-time placeholder; not part of the wallet API. */
@Getter
public class PaymentReviewDtoV1 implements IDto {
    private final String type = "payment-review";
    private final String version = "1";
}
