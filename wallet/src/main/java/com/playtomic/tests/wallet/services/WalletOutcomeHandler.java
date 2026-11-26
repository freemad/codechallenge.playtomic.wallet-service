package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.exceptions.WalletNotFoundException;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.observability.WalletLogEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Prepares business results without changing wallet operation state. */
@Service
public class WalletOutcomeHandler {

    /** Produces a wallet lookup outcome, treating an empty result as a missing wallet. */
    public Mono<Outcome<WalletModel, Error>> lookup(Mono<WalletModel> result) {
        return prepare(result).switchIfEmpty(Mono.fromSupplier(() ->
                Outcome.failure(new WalletNotFoundException().toError())));
    }

    /** Converts a mutation result or failure into the outcome returned to wallet callers. */
    public Mono<Outcome<WalletOperationModel, Error>> mutation(Mono<WalletOperationModel> result) {
        // A returned operation is result data even when its status is rejection or requires review.
        return prepare(result).doOnNext(outcome -> {
            if (outcome.isFailure()) {
                var code = outcome.getError().getCode();
                var event = code instanceof WalletErrorCode && code != WalletErrorCode.BALANCE_UPDATE_NOT_MATCHED
                        ? WalletLogEvent.MUTATION_REJECTED : WalletLogEvent.MUTATION_FAILED;
                event.fields().addKeyValue("errorCode", code.getCode()).log();
            }
        });
    }

    private static <T> Mono<Outcome<T, Error>> prepare(Mono<T> result) {
        return result.<Outcome<T, Error>>map(Outcome::success)
                .onErrorResume(Exception.class, failure -> {
                    if (!(ExceptionNormalizer.normalize(failure) instanceof BusinessException business)) {
                        return Mono.error(failure);
                    }
                    Error error = business.toError();
                    if (!(business.getErrorCode() instanceof WalletErrorCode)) {
                        // Keep infrastructure diagnostics in the cause, not in the result message.
                        error.setMessage(business.getErrorCode().getMessage());
                    }
                    return Mono.just(Outcome.failure(error));
                });
    }
}