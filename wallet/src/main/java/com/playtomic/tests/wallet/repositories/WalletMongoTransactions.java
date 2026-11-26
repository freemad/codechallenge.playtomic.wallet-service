package com.playtomic.tests.wallet.repositories;

import com.mongodb.MongoException;
import com.playtomic.tests.wallet.contracts.IWalletTransactions;
import com.playtomic.tests.wallet.observability.WalletLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/** Runs local MongoDB work without translating errors needed for rollback or recovery. */
@Component
@RequiredArgsConstructor
public class WalletMongoTransactions implements IWalletTransactions {
    private static final int MAX_RETRIES = 12;
    private static final Duration MIN_BACKOFF = Duration.ofMillis(10);
    private static final Duration MAX_BACKOFF = Duration.ofMillis(250);

    private final TransactionalOperator walletTransactions;

    /** Executes the supplied reactive work in a wallet transaction. */
    @Override
    public <T> Mono<T> execute(Mono<T> work) {
        return work.as(walletTransactions::transactional)
                .retryWhen(Retry.backoff(MAX_RETRIES, MIN_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .filter(WalletMongoTransactions::isTransientTransactionError)
                        .doBeforeRetry(signal -> WalletLogEvent.TRANSACTION_RETRY.fields().addKeyValue("attempt", signal.totalRetries() + 1).log())
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .doOnError(error -> {
                    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                        if (cause instanceof MongoException mongo) {
                            WalletLogEvent.MUTATION_FAILED.fields().addKeyValue("phase", "localTransaction")
                                    .addKeyValue("mongoCode", mongo.getCode())
                                    .addKeyValue("transientTransaction", mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL))
                                    .addKeyValue("unknownCommit", mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)).log();
                            break;
                        }
                    }
                });
    }

    /** Reports whether the failure represents a duplicate persistence key. */
    @Override
    public boolean isDuplicateKey(Throwable error) {
        return error instanceof DuplicateKeyException;
    }

    private static boolean isTransientTransactionError(Throwable error) {
        boolean transientError = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongoError) {
                if (mongoError.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    return false;
                }
                transientError |= mongoError.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
            }
        }
        return transientError;
    }
}