package com.playtomic.tests.wallet.services.client;

import com.playtomic.tests.common.contracts.StripeChargeEvidence;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.configs.WalletPaymentProviderProps;
import com.playtomic.tests.wallet.dtos.StripeDtos.ChargeReceiptDto;
import com.playtomic.tests.wallet.dtos.StripeDtos.ChargeRequestDto;
import com.playtomic.tests.wallet.dtos.StripeDtos.RefundRequestDto;
import com.playtomic.tests.wallet.http.stripe.StripeAmountTooSmallException;
import com.playtomic.tests.wallet.http.stripe.StripeServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.codec.CodecException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

/** Calls the configured charge and refund endpoints and interprets Stripe responses. */
@Component
public class StripeClient {
    private final WalletPaymentProviderProps.Provider settings;
    private final WebClient webClient;

    /** Uses the supplied HTTP client and configured provider endpoints for Stripe requests. */
    public StripeClient(WalletPaymentProviderProps props, @Qualifier("stripeWebClient") WebClient webClient) {
        this.settings = props.provider(WalletPaymentProviderProps.STRIPE);
        this.webClient = webClient;
    }

    /** Submits the charge; a confirmed HTTP 422 rejection is distinct from an uncertain transport failure. */
    public Mono<ChargeReceiptDto> charge(ChargeRequestDto request) {
        return Mono.deferContextual(context -> webClient.post()
                        .uri(settings.getChargesUri())
                        .headers(headers -> context.<String>getOrEmpty(StripeChargeEvidence.REFERENCE_HEADER)
                                .filter(ignored -> supportsFixtureEvidence())
                                .ifPresent(reference -> headers.set(StripeChargeEvidence.REFERENCE_HEADER, reference)))
                        .bodyValue(request)
                        .retrieve()
                        .onStatus(HttpStatus.UNPROCESSABLE_CONTENT::equals,
                                response -> response.createException().map(StripeAmountTooSmallException::new))
                        .onStatus(status -> !status.is2xxSuccessful(), response -> response.createException())
                        .bodyToMono(ChargeReceiptDto.class)
                        .switchIfEmpty(Mono.error(() -> new StripeServiceException("Payment receipt is missing"))))
                .transform(this::withRequestTimeout);
    }

    /** Requests a refund for a provider payment, not a return of a wallet purchase. */
    public Mono<Void> refund(RefundRequestDto request) {
        return Mono.defer(() -> {
                    var uri = UriComponentsBuilder.fromUriString(settings.getRefundsUri())
                            .encode().buildAndExpand(request.getPaymentId()).toUri();
                    return webClient.post().uri(uri).retrieve()
                            .onStatus(status -> !status.is2xxSuccessful(), response -> response.createException())
                            .toBodilessEntity().then();
                })
                .transform(this::withRequestTimeout);
    }

    private boolean supportsFixtureEvidence() {
        var uri = settings.getChargesUri();
        return "http".equals(uri.getScheme()) && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                && uri.getRawFragment() == null && ("127.0.0.1".equals(uri.getHost()) || "simulator".equals(uri.getHost()));
    }

    /** Queries the recorded charge without repeating the payment. */
    public Mono<StripeChargeEvidence> lookup(UUID reference) {
        if (!supportsFixtureEvidence()) return Mono.empty();
        return Mono.defer(() -> webClient.get().uri(UriComponentsBuilder.fromUri(settings.getChargesUri())
                        .pathSegment(reference.toString()).build().toUri())
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.NOT_FOUND)) return Mono.empty();
                    if (!response.statusCode().equals(HttpStatus.OK)) return response.createError();
                    return response.bodyToMono(StripeChargeEvidence.class);
                })).filter(evidence -> reference.equals(evidence.reference())).transform(this::withRequestTimeout);
    }

    private <T> Mono<T> withRequestTimeout(Mono<T> request) {
        return request.timeout(settings.getRequestTimeout())
                .onErrorMap(StripeClient::isUnconfirmedTransportFailure,
                        error -> new StripeServiceException("Payment provider outcome could not be confirmed", error))
                .onErrorMap(ExceptionNormalizer::normalize);
    }

    private static boolean isUnconfirmedTransportFailure(Throwable error) {
        return (error instanceof TimeoutException || error instanceof WebClientException || error instanceof CodecException)
                && !ExceptionNormalizer.isControlFlow(error) && !ExceptionNormalizer.isFatal(error);
    }
}