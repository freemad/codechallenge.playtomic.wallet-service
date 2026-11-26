package com.playtomic.tests.wallet.repositories;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mongodb.MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL;
import static com.mongodb.MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletMongoTransactionsTest {
    private static final Duration DEADLINE = Duration.ofSeconds(5);

    private final ReactiveTransactionManager manager = mock(ReactiveTransactionManager.class);
    private final ReactiveTransaction transaction = mock(ReactiveTransaction.class);
    private final WalletMongoTransactions transactions = new WalletMongoTransactions(TransactionalOperator.create(manager));

    @Test
    void retriesWrappedTransientFailuresInANewTransaction() {
        stubTransactions();
        var transientFailure = new MongoException("write conflict");
        transientFailure.addLabel(TRANSIENT_TRANSACTION_ERROR_LABEL);
        var wrapped = new DataAccessResourceFailureException("write failed", transientFailure);
        var attempts = new AtomicInteger();

        StepVerifier.withVirtualTime(() -> transactions.execute(Mono.defer(() ->
                        attempts.getAndIncrement() == 0 ? Mono.error(wrapped) : Mono.just("applied"))))
                .thenAwait(Duration.ofSeconds(1))
                .expectNext("applied").expectComplete().verify(DEADLINE);

        verify(manager, times(2)).getReactiveTransaction(any());
        verify(manager).rollback(transaction);
        verify(manager).commit(transaction);
    }

    @Test
    void unknownCommitResultVetoesRetryEvenWithATransientLabel() {
        stubTransactions();
        var commitFailure = new MongoException("commit result unknown");
        commitFailure.addLabel(UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        commitFailure.addLabel(TRANSIENT_TRANSACTION_ERROR_LABEL);
        var wrapped = new DataAccessResourceFailureException("commit failed", commitFailure);
        when(manager.commit(transaction)).thenReturn(Mono.error(wrapped));

        StepVerifier.create(transactions.execute(Mono.just("applied")))
                .expectErrorMatches(error -> error == wrapped).verify(DEADLINE);

        verify(manager).getReactiveTransaction(any());
        verify(manager).commit(transaction);
    }

    @Test
    void retryExhaustionPropagatesTheOriginalFailure() {
        stubTransactions();
        var failure = new MongoException("write conflict");
        failure.addLabel(TRANSIENT_TRANSACTION_ERROR_LABEL);

        StepVerifier.withVirtualTime(() -> transactions.execute(Mono.error(failure)))
                .thenAwait(Duration.ofSeconds(5))
                .expectErrorMatches(error -> error == failure).verify(DEADLINE);

        verify(manager, times(13)).rollback(transaction);
    }

    private void stubTransactions() {
        when(manager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(manager.commit(transaction)).thenReturn(Mono.empty());
        when(manager.rollback(transaction)).thenReturn(Mono.empty());
    }
}