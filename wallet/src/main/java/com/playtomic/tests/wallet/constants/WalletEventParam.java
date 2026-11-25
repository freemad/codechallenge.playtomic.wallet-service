package com.playtomic.tests.wallet.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Schema version assigned to new wallet balance events. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WalletEventParam {
    public static final int SCHEMA_VERSION_V1 = 1;
}