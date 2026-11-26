package com.playtomic.tests.wallet.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;

/** Carries a card top-up request for the loopback demo endpoint. */
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@JsonIgnoreProperties({"type", "version"})
public class WalletTopUpRequestDtoV1 implements IDto {
    @Serial
    private static final long serialVersionUID = -1865798060143234103L;
    private final String type = "wallet-top-up-request";
    private final String version = "1";

    private BigDecimal amount;
    private String currency;
    private String provider;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String creditCardNumber;
}
