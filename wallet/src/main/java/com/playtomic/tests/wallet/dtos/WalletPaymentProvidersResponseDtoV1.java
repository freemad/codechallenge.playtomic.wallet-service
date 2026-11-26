package com.playtomic.tests.wallet.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.Value;

import java.io.Serial;
import java.util.List;

@Value
public class WalletPaymentProvidersResponseDtoV1 implements IDto {
    @Serial
    private static final long serialVersionUID = -3865275890420874919L;

    List<String> providers;

    @Override
    @JsonIgnore
    public String getType() {
        return "wallet_payment_providers";
    }

    @Override
    @JsonIgnore
    public String getVersion() {
        return "1";
    }
}