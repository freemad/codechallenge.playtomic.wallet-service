package com.playtomic.tests.common.dtos;

import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.Getter;

import java.io.Serial;

/** Compile-time placeholder; not part of the wallet API. */
@Getter
public class PaymentReviewDtoV1 implements IDto {
    @Serial
    private static final long serialVersionUID = -4332586855274827199L;

    private final String type = "payment-review";
    private final String version = "1";
}