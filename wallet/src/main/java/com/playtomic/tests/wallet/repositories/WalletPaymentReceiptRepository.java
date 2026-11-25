package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletPaymentReceiptEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletPaymentReceiptRepository extends IRepository<WalletPaymentReceiptEntity, String> {
}