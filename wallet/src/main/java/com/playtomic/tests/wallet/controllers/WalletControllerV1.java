package com.playtomic.tests.wallet.controllers;

import com.playtomic.tests.common.dtos.ResponseDtoV1;
import com.playtomic.tests.common.dtos.StringDtoV1;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.configs.WalletTopUpHttpConfiguration.LocalTopUpRoute;
import com.playtomic.tests.wallet.configs.WalletTopUpHttpProps;
import com.playtomic.tests.wallet.dtos.WalletPaymentProvidersResponseDtoV1;
import com.playtomic.tests.wallet.dtos.WalletTopUpRequestDtoV1;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.mappers.WalletResponseMapperV1;
import com.playtomic.tests.wallet.mappers.WalletTopUpResponseMapperV1;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.services.WalletLookupService;
import com.playtomic.tests.wallet.services.WalletMutationService;
import com.playtomic.tests.wallet.services.WalletTopUpProviders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static com.playtomic.tests.common.constants.HttpParam.HEADER_IDEMPOTENCY_KEY;
import static com.playtomic.tests.wallet.controllers.WalletHttpResponses.toResponse;

/**
 * Exposes wallet lookup and the opt-in local top-up handlers.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletControllerV1 {

    private final WalletLookupService walletService;
    private final WalletResponseMapperV1 walletResponseMapper;
    private final WalletMutationService mutations;
    private final WalletTopUpResponseMapperV1 topUpMapper;
    private final WalletTopUpProviders paymentProviders;
    private final WalletTopUpHttpProps topUpHttpProps;

    private static final String PAYMENT_PROVIDERS_SEGMENT = "payment-providers";
    private static final String WALLET_PATH = "/{id:(?!(?:log|" + PAYMENT_PROVIDERS_SEGMENT + ")\\z)[\\s\\S]+}";

    /**
     * Returns a timestamp in the response envelope for a smoke check.
     */
    @GetMapping("/log")
    public Mono<ResponseEntity<ResponseDtoV1>> log() {
        return Mono.fromCallable(() -> toResponse(new StringDtoV1().setString(String.valueOf(System.currentTimeMillis())),
                CacheControl.empty()));
    }

    // Reserve the discovery path even when its handler is not registered.

    /**
     * Looks up the wallet by identifier and returns its result or mapped error in the HTTP envelope.
     */
    @GetMapping(WALLET_PATH)
    public Mono<ResponseEntity<ResponseDtoV1>> getWallet(@PathVariable("id") String id, ServerWebExchange exchange) {
        return walletService.findById(id).map(outcome -> toResponse(outcome, exchange.getRequest().getId(),
                walletResponseMapper::toDto, lookupStatus(outcome), CacheControl.noStore()));
    }

    /**
     * Lists configured top-up providers without probing availability or charging a card.
     */
    @LocalTopUpRoute
    @GetMapping(path = "/" + PAYMENT_PROVIDERS_SEGMENT, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDtoV1> getPaymentProviders() {
        return toResponse(new WalletPaymentProvidersResponseDtoV1(paymentProviders.activeIds()), CacheControl.noStore());
    }

    /**
     * Handles a local-demo card top-up; requires the configured synthetic card and exactly one nonblank idempotency key.
     */
    @LocalTopUpRoute
    @PostMapping(path = WALLET_PATH + "/top-ups", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ResponseDtoV1>> topUp(@PathVariable("id") String id,
                                                     @RequestHeader(HEADER_IDEMPOTENCY_KEY) String key,
                                                     @RequestBody WalletTopUpRequestDtoV1 request,
                                                     ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getId();
        var keys = exchange.getRequest().getHeaders().get(HEADER_IDEMPOTENCY_KEY);
        if (keys == null || keys.size() != 1 || key.isBlank()) {
            return Mono.just(invalidTopUp("Exactly one nonblank idempotency key is required", correlationId));
        }
        if (!topUpHttpProps.acceptsCardNumber(request.getCreditCardNumber())) {
            return Mono.just(invalidTopUp("Only the documented synthetic card is accepted", correlationId));
        }
        return mutations.topUp(id, key, request.getAmount(), request.getCurrency(), request.getProvider(),
                        request.getCreditCardNumber())
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)))
                .map(outcome -> toTopUpResponse(outcome, correlationId));
    }

    private ResponseEntity<ResponseDtoV1> invalidTopUp(String message, String correlationId) {
        return toTopUpResponse(Outcome.failure(new BusinessException(WalletErrorCode.INVALID_REQUEST, message).toError()),
                correlationId);
    }

    private ResponseEntity<ResponseDtoV1> toTopUpResponse(Outcome<WalletOperationModel, Error> outcome, String correlationId) {
        return toResponse(outcome, correlationId, topUpMapper::toDto, topUpStatus(outcome), CacheControl.noStore());
    }

    private static HttpStatus topUpStatus(Outcome<WalletOperationModel, Error> outcome) {
        if (!outcome.isFailure()) {
            return switch (outcome.getResponse().getStatus()) {
                case WALLET_OPERATION_STATUS_SUCCEEDED -> HttpStatus.OK;
                case WALLET_OPERATION_STATUS_PAYMENT_REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
                case WALLET_OPERATION_STATUS_PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                case WALLET_OPERATION_STATUS_PENDING,
                     WALLET_OPERATION_STATUS_REVIEW_REQUIRED,
                     WALLET_OPERATION_STATUS_RECEIPT_RECORDED -> HttpStatus.ACCEPTED;
                default -> throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
            };
        }
        if (outcome.getError().getCode() instanceof WalletErrorCode code) {
            return switch (code) {
                case INVALID_WALLET_ID,
                     INVALID_REQUEST,
                     CURRENCY_MISMATCH -> HttpStatus.BAD_REQUEST;
                case WALLET_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static HttpStatus lookupStatus(Outcome<WalletModel, Error> outcome) {
        if (!outcome.isFailure()) {
            return HttpStatus.OK;
        }
        var code = outcome.getError().getCode();
        if (code == WalletErrorCode.INVALID_WALLET_ID) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == WalletErrorCode.WALLET_NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}