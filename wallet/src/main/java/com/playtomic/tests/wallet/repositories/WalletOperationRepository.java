package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletOperationEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletOperationRepository extends IRepository<WalletOperationEntity, String>, WalletOperationMongoOperations {
}