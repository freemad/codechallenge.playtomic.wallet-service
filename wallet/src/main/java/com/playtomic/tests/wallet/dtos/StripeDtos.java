package com.playtomic.tests.wallet.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.io.Serial;
import java.math.BigDecimal;

import static com.playtomic.tests.wallet.constants.StripeParam.AMOUNT_FIELD;
import static com.playtomic.tests.wallet.constants.StripeParam.CREDIT_CARD_FIELD;

/** Groups request and receipt types for the Stripe simulator protocol. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StripeDtos {
    /** Provides the version shared by Stripe transport objects. */
    @JsonIgnoreProperties({"type", "version"})
    public interface StripeDto extends IDto {
        /** Returns the version of the transport representation. */
        @Override
        default String getVersion() {
            return "1";
        }
    }

    /** Carries the card and amount sent to the charge endpoint. */
    @Value
    public static class ChargeRequestDto implements StripeDto {
        @Serial
        private static final long serialVersionUID = -1420630416398220163L;

        @NonNull
        @ToString.Exclude
        @JsonProperty(CREDIT_CARD_FIELD)
        String creditCardNumber;

        @NonNull
        @JsonProperty(AMOUNT_FIELD)
        BigDecimal amount;

        /** Returns the transport type discriminator. */
        @Override
        public String getType() {
            return "stripe-charge-request";
        }
    }

    /** Carries the receipt identifier returned by the charge endpoint. */
    @Value
    public static class ChargeReceiptDto implements StripeDto {
        @Serial
        private static final long serialVersionUID = 3868765199000627713L;

        String id;

        /** Creates a wire receipt for the supplied payment identifier. */
        @JsonCreator
        public ChargeReceiptDto(@JsonProperty(value = "id", required = true) String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Provider payment ID is required");
            }
            this.id = id;
        }

        /** Returns the transport type discriminator. */
        @Override
        public String getType() {
            return "stripe-charge-receipt";
        }
    }

    /** Identifies the payment to refund through the provider. */
    @Value
    public static class RefundRequestDto implements StripeDto {
        @Serial
        private static final long serialVersionUID = 618452829140960113L;

        @NonNull
        String paymentId;

        /** Returns the transport type discriminator. */
        @Override
        public String getType() {
            return "stripe-refund-request";
        }
    }
}

