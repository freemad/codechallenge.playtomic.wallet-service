package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletEntity;
import org.springframework.stereotype.Repository;

/** Provides reactive persistence access for wallet records. */
@Repository
public interface WalletRepository extends IRepository<WalletEntity, String> {
}
