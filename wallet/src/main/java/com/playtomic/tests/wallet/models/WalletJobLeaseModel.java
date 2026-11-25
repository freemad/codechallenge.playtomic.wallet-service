package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.Instant;

@Value
@Accessors(chain = true)
public class WalletJobLeaseModel implements IModel {
    @Serial
    private static final long serialVersionUID = -5380546533707176625L;

    String id;
    @ToString.Exclude
    String token;
    Instant expiresAt;
}