package com.playtomic.tests.wallet.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.Value;

import java.io.Serial;
import java.math.BigDecimal;

@Value
@JsonIgnoreProperties({"type", "version"})
public class WalletTopUpResponseDtoV1 implements IDto {
    @Serial
    private static final long serialVersionUID = 1376238607883952891L;
    String type = "wallet-top-up-response";
    String version = "1";

    String operationId;
    String walletId;
    BigDecimal amount;
    String currency;
    String provider;
    String status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    BigDecimal resultingBalance;
}