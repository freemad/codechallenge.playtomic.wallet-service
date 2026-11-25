package com.playtomic.tests.wallet.exceptions;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.enums.WalletErrorCode;

import java.io.Serial;

public class WalletNotFoundException extends BusinessException {

    @Serial
    private static final long serialVersionUID = -2929597158173036145L;

    public WalletNotFoundException() {
        super(WalletErrorCode.WALLET_NOT_FOUND);
    }
}