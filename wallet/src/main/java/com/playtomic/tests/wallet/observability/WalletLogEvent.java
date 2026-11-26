package com.playtomic.tests.wallet.observability;

import com.playtomic.tests.common.observability.StructuredEvents;
import lombok.RequiredArgsConstructor;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.regex.Pattern;

import static org.slf4j.event.Level.*;

/** Wallet diagnostic vocabulary. IDs are derived, never raw wallet or provider identifiers. */
@RequiredArgsConstructor
public enum WalletLogEvent {
    RESERVED(INFO, "operation.reserved"), REPLAY(DEBUG, "operation.replay"), CONFLICT(DEBUG, "operation.conflict"),
    MUTATION_REJECTED(DEBUG, "mutation.rejected"), MUTATION_FAILED(ERROR, "mutation.failed"),
    DISPATCH(INFO, "payment.dispatch"), RECEIVED(INFO, "payment.receipt_returned"), DECLINED(INFO, "payment.declined"),
    BREAKER_OPEN(WARN, "payment.not_permitted"), UNCONFIRMED(WARN, "payment.unconfirmed"), CANCELLED(WARN, "payment.cancelled"),
    RECEIPT_COMMITTED(INFO, "receipt.committed"), DUPLICATE_RECEIPT(WARN, "receipt.duplicate"),
    FINALIZED(INFO, "operation.finalized"), TRANSACTION_RETRY(DEBUG, "transaction.retry"),
    REVIEW(WARN, "reconciliation.review"), RECOVERY_FAILED(WARN, "reconciliation.apply_failed"),
    RECONCILIATION_FAILED(ERROR, "reconciliation.run_failed"),
    BROKER_ACCEPTED(DEBUG, "outbox.broker_accepted"), ACKNOWLEDGED(INFO, "outbox.acknowledged"),
    LOST_LEASE(WARN, "outbox.lost_lease"), DELIVERY_INCOMPLETE(WARN, "outbox.delivery_incomplete"), RELAY_FAILED(ERROR, "outbox.run_failed");

    private static final Pattern OPERATION_ID = Pattern.compile("[0-9a-f]{64}");
    private final Level level;
    private final String event;

    public static final StructuredEvents EVENTS = new StructuredEvents("wallet");

    public LoggingEventBuilder fields() {
        return EVENTS.fields(level, event);
    }

    public LoggingEventBuilder operation(String derivedId) {
        var fields = fields();
        if (derivedId != null && OPERATION_ID.matcher(derivedId).matches()) {
            fields.addKeyValue("operationId", derivedId);
        }
        return fields;
    }
}
