package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WalletOutcome {
    public enum Completion { COMPLETED }

    public static <T> Mono<Outcome<Optional<T>, Error>> optional(Mono<T> result) {
        return result.map(Optional::of).defaultIfEmpty(Optional.empty()).map(Outcome::success);
    }

    public static Mono<Outcome<Completion, Error>> completed(Mono<Void> work) {
        return work.then(Mono.fromSupplier(() -> Outcome.success(Completion.COMPLETED)));
    }

    public static <T> Mono<T> value(Outcome<T, Error> outcome) {
        if (!outcome.isFailure()) {
            return Mono.just(outcome.getData());
        }
        Error error = outcome.getError();
        return Mono.error(error.getException() != null ? error.getException()
                : new BusinessException(error.getCode(), error.getMessage()));
    }

    public static <T> Mono<T> optionalValue(Outcome<Optional<T>, Error> outcome) {
        return value(outcome).flatMap(Mono::justOrEmpty);
    }

    public static <T> Mono<T> required(Mono<T> result, String message) {
        return result.switchIfEmpty(Mono.error(() -> new IllegalStateException(message)));
    }

    public static <T> Mono<Outcome<T, Error>> from(Mono<T> result) {
        return result.<Outcome<T, Error>>map(Outcome::success)
                .onErrorResume(Exception.class, failure -> {
                    if (!(ExceptionNormalizer.normalize(failure) instanceof BusinessException business)) {
                        return Mono.error(failure);
                    }
                    // Keep normalized diagnostics and causes internal; only wallet business details are public.
                    Error error = business.toError();
                    if (!(business.getErrorCode() instanceof WalletErrorCode)) {
                        // The normalized exception retains the cause; do not expose its diagnostics in the outcome.
                        error.setMessage(business.getErrorCode().getMessage());
                    }
                    return Mono.just(Outcome.failure(error));
                });
    }
}