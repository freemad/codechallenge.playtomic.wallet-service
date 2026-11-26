package com.playtomic.tests.wallet.entities;

import com.playtomic.tests.common.contracts.models.IEntity;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.io.Serial;
import java.time.Instant;

/** Stores the token and expiration that fence worker access to a job. */
@Value
@Accessors(chain = true)
@RequiredArgsConstructor
@Document("wallet_job_leases")
public class WalletJobLeaseEntity implements IEntity<String> {
    @Serial
    private static final long serialVersionUID = -8119478220547023280L;

    @MongoId
    String id;
    String token;
    Instant expiresAt;

    @Transient
    @NonFinal
    @Setter
    @Accessors(chain = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    boolean newRecord;
}
