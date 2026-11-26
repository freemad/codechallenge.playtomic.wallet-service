package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.wallet.contracts.IWalletTransactions;
import com.playtomic.tests.wallet.entities.WalletAuditScheduleEntity;
import com.playtomic.tests.wallet.entities.WalletPostingEntity;
import com.playtomic.tests.wallet.repositories.WalletAuditMongoOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import static com.playtomic.tests.wallet.observability.WalletLogEvent.AUDIT_DISCREPANCY;

/** Compares a historical wallet snapshot with the ledger through the same sequence. */
@Service
@RequiredArgsConstructor
public class WalletBalanceAuditService {
    private final WalletAuditMongoOperations repository;
    private final IWalletTransactions transactions;

    /** Claims the next due wallet period and audits it if a claim is available. */
    public Mono<Boolean> auditNext(Duration lease) {
        return Mono.defer(() -> repository.claim(Instant.now(), lease)).flatMap(this::audit).defaultIfEmpty(false);
    }

    /** Compares the claimed cutoff snapshot with its ledger and records a checkpoint or discrepancy. */
    public Mono<Boolean> audit(WalletAuditScheduleEntity claim) {
        return repository.atCutoff(claim).flatMap(snapshot -> {
            long through = snapshot.getLedgerSequence() == null ? 0 : snapshot.getLedgerSequence();
            return repository.postings(claim, through).reduceWith(() -> new Total(claim), Total::add).flatMap(total -> {
                boolean continuous = total.continuous && through == total.sequence;
                boolean matched = continuous && snapshot.getCurrency().equals(claim.getCurrency())
                        && total.balance.compareTo(snapshot.getBalance()) == 0;
                return transactions.execute(Mono.defer(() -> {
                    Instant now = Instant.now();
                    return repository.fence(claim, matched, now).filter(Boolean::booleanValue).flatMap(won ->
                            (matched ? repository.checkpoint(claim, through, total.balance, now)
                                    : repository.discrepancy(claim, snapshot, total.balance, continuous, now)).thenReturn(true));
                })).doOnNext(won -> { if (!matched) AUDIT_DISCREPANCY.fields().log(); }).defaultIfEmpty(false);
            });
        });
    }

    private static final class Total {
        private final String currency;
        private BigDecimal balance;
        private long sequence;
        private boolean continuous = true;
        Total(WalletAuditScheduleEntity opening) {
            currency = opening.getCurrency();
            balance = opening.getOpeningBalance();
            sequence = opening.getOpeningSequence();
        }
        Total add(WalletPostingEntity posting) {
            continuous &= posting.getSequence() == sequence + 1 && currency.equals(posting.getCurrency());
            balance = balance.add(posting.getDelta());
            sequence = posting.getSequence();
            return this;
        }
    }
}