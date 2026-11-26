package com.playtomic.tests.wallet.services.client;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.codec.CodecException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@RequiredArgsConstructor
final class ProviderHttpClient {
    private final WebClient client;
    private final Duration requestTimeout;

    Mono<ResponseEntity<JsonNode>> post(URI uri, Object body, HttpStatus expected, HttpStatus alternate) {
        return Mono.defer(() -> {
                    var request = client.post().uri(uri).accept(MediaType.APPLICATION_JSON);
                    return (body == null ? request : request.contentType(MediaType.APPLICATION_JSON).bodyValue(body))
                            .exchangeToMono(response -> {
                                var status = response.statusCode();
                                if (!status.equals(expected) && !status.equals(alternate)) {
                                    return Mono.error(new IllegalStateException("Payment provider returned HTTP " + status.value()));
                                }
                                return response.bodyToMono(JsonNode.class)
                                        .switchIfEmpty(Mono.error(new IllegalStateException("Missing payment provider response")))
                                        .map(payload -> ResponseEntity.status(status).body(payload));
                            });
                })
                .timeout(requestTimeout)
                .onErrorMap(Exception.class, error -> ExceptionNormalizer.normalize(error,
                        CommonErrorCode.EXTERNAL_SERVICE_EXCEPTION, "Payment provider response could not be confirmed"));
    }
}