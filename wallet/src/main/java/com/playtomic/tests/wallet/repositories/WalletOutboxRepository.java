package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletOutboxRepository extends IRepository<WalletOutboxEntity, String>, WalletOutboxMongoOperations {
}