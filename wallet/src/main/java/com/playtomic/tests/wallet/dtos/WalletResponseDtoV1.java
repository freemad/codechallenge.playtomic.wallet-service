package com.playtomic.tests.wallet.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@JsonIgnoreProperties({"type", "version"})
public class WalletResponseDtoV1 implements IDto {
    @Serial
    private static final long serialVersionUID = -7084387527586703071L;
    private final String type = "wallet-response";
    private final String version = "1";

    String id;
    BigDecimal balance;
    String currency;
}
