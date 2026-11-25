package com.playtomic.tests.wallet.http.stripe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.http.NonRetryingWebClientFactory;
import com.playtomic.tests.wallet.configs.StripeSimulatorProps;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.core.codec.CodecException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static com.playtomic.tests.wallet.constants.StripeSimulatorParam.AMOUNT_FIELD;
import static com.playtomic.tests.wallet.constants.StripeSimulatorParam.CREDIT_CARD_FIELD;


/**
 * Handles communication with the supplied payment simulator.
 */
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeSimulatorProps simulatorProps;
    private final WebClient webClient;

    /**
     * Charges money in the credit card.
     *
     * Ignore the fact that no CVC or expiration date are provided.
     *
     * @param creditCardNumber The number of the credit card
     * @param amount The amount that will be charged.
     */
    public Mono<Payment> charge(@NonNull String creditCardNumber, @NonNull BigDecimal amount) {
        return webClient.post()
                .uri(simulatorProps.getChargesUri())
                .bodyValue(new ChargeRequest(creditCardNumber, amount))
                .retrieve()
                .onStatus(HttpStatus.UNPROCESSABLE_CONTENT::equals,
                        response -> Mono.just(new StripeAmountTooSmallException()))
                .onStatus(status -> !status.is2xxSuccessful(), response -> response.createException())
                .bodyToMono(Payment.class)
                .switchIfEmpty(Mono.error(() -> new StripeServiceException("Payment receipt is missing")))
                .transform(this::withRequestTimeout);
    }

    /**
     * Refunds the specified payment.
     */
    public Mono<Void> refund(@NonNull String paymentId) {
        URI uri = UriComponentsBuilder.fromUriString(simulatorProps.getRefundsUri())
                .encode()
                .buildAndExpand(paymentId)
                .toUri();
        return webClient.post()
                .uri(uri)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> response.createException())
                .toBodilessEntity()
                .then()
                .transform(this::withRequestTimeout);
    }

    private <T> Mono<T> withRequestTimeout(Mono<T> request) {
        return request.timeout(simulatorProps.getRequestTimeout())
                .onErrorMap(error -> error instanceof TimeoutException
                                || error instanceof WebClientException || error instanceof CodecException,
                        error -> new StripeServiceException("Payment provider outcome could not be confirmed", error));
    }

    @AllArgsConstructor
    @Accessors(chain = true)
    private static class ChargeRequest {

        @NonNull
        @JsonProperty(CREDIT_CARD_FIELD)
        String creditCardNumber;

        @NonNull
        @JsonProperty(AMOUNT_FIELD)
        BigDecimal amount;
    }
}