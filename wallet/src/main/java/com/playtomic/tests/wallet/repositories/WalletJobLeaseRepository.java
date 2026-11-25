package com.playtomic.tests.wallet.repositories;

import com.playtomic.tests.common.contracts.models.IRepository;
import com.playtomic.tests.wallet.entities.WalletJobLeaseEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletJobLeaseRepository extends IRepository<WalletJobLeaseEntity, String>, WalletJobLeaseMongoOperations {
}