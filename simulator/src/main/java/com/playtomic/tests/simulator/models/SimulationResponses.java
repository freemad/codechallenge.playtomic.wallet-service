package com.playtomic.tests.simulator.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.io.Serial;
import java.time.Duration;

/** Supplies shared HTTP responses for simulated provider scenarios. */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class SimulationResponses implements IModel {
    @Serial
    private static final long serialVersionUID = 58835290838312370L;

    private static final Duration TIMEOUT_DELAY = Duration.ofSeconds(10);

    /** Emits a gateway-timeout response after the simulator delay. */
    public static <T> Mono<ResponseEntity<T>> timeout() {
        // Longer than the wallet's default request timeout, but bounded for other clients.
        return Mono.delay(TIMEOUT_DELAY)
                .map(ignored -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build());
    }
}

