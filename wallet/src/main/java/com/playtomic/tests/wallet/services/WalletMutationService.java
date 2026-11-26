package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.resilience.ReactiveCircuitBreaker;
import com.playtomic.tests.wallet.enums.WalletOperationType;
import com.playtomic.tests.wallet.exceptions.WalletMutationException;
import com.playtomic.tests.wallet.http.stripe.StripeAmountTooSmallException;
import com.playtomic.tests.wallet.http.stripe.StripeServiceException;
import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.observability.WalletMutationMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Function;

import static com.playtomic.tests.wallet.configs.WalletPaymentProviderProps.STRIPE;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.*;
import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_SPEND;
import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP;
import static com.playtomic.tests.wallet.exceptions.WalletMutationException.Reason.*;

@Service
@RequiredArgsConstructor
public class WalletMutationService {
    private static final String OPERATION_ID_DIGEST = "SHA-256";
    private static final String WALLET_ID_LENGTH_SEPARATOR = ":";

    private final WalletLookupService lookupService;
    private final WalletOperationService operationService;
    private final ReactiveCircuitBreaker walletPaymentCircuitBreaker;
    private final WalletMutationMetrics metrics;
    private final WalletOutcomeHandler outcomeHandler;
    private final WalletTopUpProviders paymentProviders;

    public Mono<Outcome<WalletOperationModel, Error>> topUp(String walletId, String key, BigDecimal amount,
                                                             String currency, String creditCardNumber) {
        return topUp(walletId, key, amount, currency, STRIPE, creditCardNumber);
    }

    public Mono<Outcome<WalletOperationModel, Error>> topUp(String walletId, String key, BigDecimal amount,
                                                             String currency, String provider, String creditCardNumber) {
        return outcomeHandler.mutation(metrics.mutation(WALLET_OPERATION_TYPE_TOP_UP, Mono.defer(() -> {
            if (creditCardNumber == null || creditCardNumber.isBlank()) {
                return Mono.error(invalid("Credit card number is required"));
            }
            if (provider == null || provider.isBlank()) {
                return Mono.error(invalid("Payment provider is required"));
            }
            return execute(request(walletId, key, amount, currency, WALLET_OPERATION_TYPE_TOP_UP, provider),
                    reserved -> charge(reserved, creditCardNumber));
        })));
    }

    public Mono<Outcome<WalletOperationModel, Error>> spend(String walletId, String key, BigDecimal amount,
                                                             String currency) {
        return outcomeHandler.mutation(metrics.mutation(WALLET_OPERATION_TYPE_SPEND, Mono.defer(() ->
                execute(request(walletId, key, amount, currency, WALLET_OPERATION_TYPE_SPEND, null), reserved -> apply(reserved.getId())))));
    }

    private Mono<WalletOperationModel> execute(WalletOperationModel request,
                                              Function<WalletOperationModel, Mono<WalletOperationModel>> onReserved) {
        String id = request.getId();
        return operationService.find(id).flatMap(WalletOutcome::optionalValue)
                .flatMap(existing -> replay(existing, request))
                .switchIfEmpty(Mono.defer(() -> {
                    if (request.getType() == WALLET_OPERATION_TYPE_TOP_UP && !paymentProviders.isActive(request.getProvider())) {
                        return Mono.error(invalid("Payment provider is not active for top-ups"));
                    }
                    return lookupService.findById(request.getWalletId()).flatMap(WalletOutcome::value).flatMap(wallet -> {
                        if (!wallet.getCurrency().equals(request.getCurrency())) {
                            return Mono.error(new WalletMutationException(CURRENCY_MISMATCH,
                                    "Operation currency must match the wallet"));
                        }
                        return operationService.reserve(request).flatMap(WalletOutcome::optionalValue).flatMap(onReserved)
                                .switchIfEmpty(Mono.defer(() -> operationService.find(id)
                                        .flatMap(WalletOutcome::optionalValue)
                                        .switchIfEmpty(Mono.error(() -> new IllegalStateException(
                                                "Reservation lost but the winner is not yet visible")))
                                        .flatMap(existing -> replay(existing, request))));
                    });
                }));
    }

    private Mono<WalletOperationModel> replay(WalletOperationModel existing, WalletOperationModel request) {
        if (!existing.matches(request)) {
            return Mono.error(new WalletMutationException(IDEMPOTENCY_CONFLICT,
                    "Idempotency key already identifies a different operation"));
        }
        if (existing.getStatus() == WALLET_OPERATION_STATUS_RECEIPT_RECORDED
                || (existing.getType() == WALLET_OPERATION_TYPE_SPEND && existing.getStatus() == WALLET_OPERATION_STATUS_PENDING)) {
            return apply(existing.getId());
        }
        return Mono.just(existing);
    }

    private Mono<WalletOperationModel> charge(WalletOperationModel operation, String card) {
        String id = operation.getId();
        // Only errors from this provider call are classified as payment outcomes. DB errors propagate.
        return walletPaymentCircuitBreaker.protect(metrics.charge(Mono.defer(() ->
                        paymentProviders.charge(operation.getProvider(), new CardChargeModel(card, operation.getAmount())))))
                .materialize().flatMap(signal -> {
                    if (signal.isOnNext()) {
                        return operationService.recordReceipt(id, signal.get().getId()).flatMap(WalletOutcome::optionalValue)
                                .flatMap(recorded -> recorded.getStatus() == WALLET_OPERATION_STATUS_RECEIPT_RECORDED
                                        ? apply(id) : Mono.just(recorded));
                    }
                    Throwable error = signal.getThrowable();
                    if (error instanceof StripeAmountTooSmallException) {
                        return operationService.markPaymentOutcome(id, WALLET_OPERATION_STATUS_PAYMENT_REJECTED).flatMap(WalletOutcome::optionalValue);
                    }
                    if (error instanceof CallNotPermittedException) {
                        return operationService.markPaymentOutcome(id, WALLET_OPERATION_STATUS_PROVIDER_UNAVAILABLE).flatMap(WalletOutcome::optionalValue);
                    }
                    if (error == null || error instanceof StripeServiceException) {
                        return operationService.markPaymentOutcome(id, WALLET_OPERATION_STATUS_REVIEW_REQUIRED).flatMap(WalletOutcome::optionalValue);
                    }
                    return Mono.error(error);
                }).switchIfEmpty(missingOperation());
    }

    private Mono<WalletOperationModel> apply(String id) {
        return operationService.apply(id).flatMap(WalletOutcome::optionalValue).switchIfEmpty(missingOperation());
    }

    private static Mono<WalletOperationModel> missingOperation() {
        return Mono.error(() -> new IllegalStateException("Reserved wallet operation is missing"));
    }

    private static WalletOperationModel request(String walletId, String key, BigDecimal amount, String currency,
                                                WalletOperationType type, String provider) {
        var utf8 = StandardCharsets.UTF_8.newEncoder();
        if (walletId == null || walletId.isBlank() || !utf8.canEncode(walletId)
                || key == null || key.isBlank() || !utf8.canEncode(key)
                || currency == null || currency.isBlank() || amount == null || amount.signum() <= 0) {
            throw invalid("Wallet ID, idempotency key, currency and positive amount are required");
        }
        return new WalletOperationModel(operationId(walletId, key), walletId, type, amount, currency,
                WALLET_OPERATION_STATUS_PENDING, null, Instant.now(), provider);
    }

    private static String operationId(String walletId, String key) {
        try {
            // The length prefix makes the wallet/key boundary unambiguous, even with arbitrary separators.
            String scopedKey = walletId.length() + WALLET_ID_LENGTH_SEPARATOR + walletId + key;
            return HexFormat.of().formatHex(MessageDigest.getInstance(OPERATION_ID_DIGEST)
                    .digest(scopedKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(OPERATION_ID_DIGEST + " is not available", error);
        }
    }

    private static WalletMutationException invalid(String message) {
        return new WalletMutationException(INVALID_REQUEST, message);
    }
}