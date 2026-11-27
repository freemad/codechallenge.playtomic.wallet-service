package com.playtomic.tests.simulator.services;

import com.playtomic.tests.common.contracts.StripeChargeEvidence;
import com.playtomic.tests.simulator.entities.StripeChargeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Only the provider writes charge evidence. No update or delete path is exposed. */
@Component
@RequiredArgsConstructor
public final class StripeEvidenceStore {
    private final ReactiveMongoTemplate mongo;

    public Mono<StripeChargeEvidence> record(StripeChargeEvidence proposed) {
        return mongo.insert(StripeChargeDocument.from(proposed))
                .map(StripeChargeDocument::evidence)
                .onErrorResume(DuplicateKeyException.class, collision -> resolveReplay(proposed, collision));
    }

    private Mono<StripeChargeEvidence> resolveReplay(StripeChargeEvidence proposed, DuplicateKeyException collision) {
        return lookup(proposed.reference())
                .switchIfEmpty(Mono.error(collision))
                .filter(existing -> sameAmountAndCurrency(existing, proposed))
                .switchIfEmpty(Mono.error(ReferenceConflict::new));
    }

    private static boolean sameAmountAndCurrency(StripeChargeEvidence existing, StripeChargeEvidence proposed) {
        return existing.currency().equals(proposed.currency()) && existing.amount().compareTo(proposed.amount()) == 0;
    }

    public Mono<StripeChargeEvidence> lookup(UUID reference) {
        return mongo.findById(reference.toString(), StripeChargeDocument.class).map(StripeChargeDocument::evidence);
    }

    public static final class ReferenceConflict extends RuntimeException { }
}