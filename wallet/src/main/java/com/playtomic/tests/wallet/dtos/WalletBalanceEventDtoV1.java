package com.playtomic.tests.wallet.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;

/** Version-one payload describing a wallet balance event. */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties({"type", "version"})
@AllArgsConstructor
public class WalletBalanceEventDtoV1 implements IDto {
    @Serial
    private static final long serialVersionUID = -4345283164983344488L;
    private final String type = "wallet-balance-event";
    private final String version = "1";

    String eventId;
    String walletId;
    String eventType;
    int schemaVersion;
    BigDecimal amount;
    String currency;
    BigDecimal resultingBalance;
    Instant occurredAt;
}