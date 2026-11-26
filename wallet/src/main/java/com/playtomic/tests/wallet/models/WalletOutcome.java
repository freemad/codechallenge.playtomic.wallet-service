package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.enums.WalletCompletion;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Optional;

/** Bridges reactive completion, optional results and the shared outcome representation. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WalletOutcome {
    /** Wraps a value or empty completion as a successful optional outcome. */
    public static <T> Mono<Outcome<Optional<T>, Error>> optional(Mono<T> result) {
        return result.map(Optional::of).defaultIfEmpty(Optional.empty()).map(Outcome::success);
    }

    /** Converts successful completion without data to an explicit wallet completion outcome. */
    public static Mono<Outcome<WalletCompletion, Error>> completed(Mono<Void> work) {
        return work.then(Mono.fromSupplier(() -> Outcome.success(WalletCompletion.WALLET_COMPLETION_COMPLETED)));
    }

    /** Extracts the successful value or emits the recorded failure as an error signal. */
    public static <T> Mono<T> value(Outcome<T, Error> outcome) {
        if (!outcome.isFailure()) {
            return Mono.just(outcome.getData());
        }
        Error error = outcome.getError();
        return Mono.error(error.getException() != null ? error.getException()
                : new BusinessException(error.getCode(), error.getMessage()));
    }

    /** Extracts an optional outcome, completing empty when the successful result has no value. */
    public static <T> Mono<T> optionalValue(Outcome<Optional<T>, Error> outcome) {
        return value(outcome).flatMap(Mono::justOrEmpty);
    }

    /** Converts an unexpected empty completion into an application failure. */
    public static <T> Mono<T> required(Mono<T> result, String message) {
        return result.switchIfEmpty(Mono.error(() -> ExceptionNormalizer.normalize(new IllegalStateException(message))));
    }
}