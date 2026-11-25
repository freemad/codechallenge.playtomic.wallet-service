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

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StripeDtos {
    @JsonIgnoreProperties({"type", "version"})
    public interface StripeDto extends IDto {
        @Override
        default String getVersion() {
            return "1";
        }
    }

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

        @Override
        public String getType() {
            return "stripe-charge-request";
        }
    }

    @Value
    public static class ChargeReceiptDto implements StripeDto {
        @Serial
        private static final long serialVersionUID = 3868765199000627713L;

        String id;

        @JsonCreator
        public ChargeReceiptDto(@JsonProperty(value = "id", required = true) String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Provider payment ID is required");
            }
            this.id = id;
        }

        @Override
        public String getType() {
            return "stripe-charge-receipt";
        }
    }

    @Value
    public static class RefundRequestDto implements StripeDto {
        @Serial
        private static final long serialVersionUID = 618452829140960113L;

        @NonNull
        String paymentId;

        @Override
        public String getType() {
            return "stripe-refund-request";
        }
    }
}

