package com.playtomic.tests.simulator.entities;

import com.playtomic.tests.common.contracts.StripeChargeEvidence;
import com.playtomic.tests.common.enums.StripeChargeStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable provider decision. BSON status codes are stable, not enum ordinals. */
@Document("stripe_charges")
public record StripeChargeDocument(@Id String reference, int status, String receiptId,
                                  @Field(targetType = FieldType.DECIMAL128) BigDecimal amount, String currency) {
    private static final int ACCEPTED = 1;
    private static final int DECLINED = 2;

    public static StripeChargeDocument from(StripeChargeEvidence charge) {
        int code = switch (charge.status()) {
            case ACCEPTED -> ACCEPTED;
            case DECLINED -> DECLINED;
        };
        return new StripeChargeDocument(charge.reference().toString(), code, charge.receiptId(), charge.amount(), charge.currency());
    }

    public StripeChargeEvidence evidence() {
        var state = switch (status) {
            case ACCEPTED -> StripeChargeStatus.ACCEPTED;
            case DECLINED -> StripeChargeStatus.DECLINED;
            default -> throw new IllegalStateException("Unknown Stripe charge state");
        };
        return new StripeChargeEvidence(UUID.fromString(reference), state, receiptId, amount, currency);
    }
}
