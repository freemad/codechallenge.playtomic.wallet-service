package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.Value;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.Instant;

@Value
@Accessors(chain = true)
public class WalletPaymentReceiptModel implements IModel {
    @Serial
    private static final long serialVersionUID = -3519949837267530499L;

    String providerPaymentId;
    String operationId;
    Instant recordedAt;
}
