package com.playtomic.tests.simulator.controllers;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SimulationResponses {
    private static final Duration TIMEOUT_DELAY = Duration.ofSeconds(10);

    static <T> Mono<ResponseEntity<T>> timeout() {
        // Longer than the wallet's default request timeout, but bounded for other clients.
        return Mono.delay(TIMEOUT_DELAY)
                .map(ignored -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build());
    }
}