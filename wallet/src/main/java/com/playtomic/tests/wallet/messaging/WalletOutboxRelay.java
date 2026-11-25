package com.playtomic.tests.wallet.messaging;

import com.playtomic.tests.wallet.dtos.WalletBalanceEventDtoV1;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.models.WalletOutcome;
import com.playtomic.tests.wallet.services.WalletOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "wallet.outbox.enabled", havingValue = "true")
public class WalletOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(WalletOutboxRelay.class);
    private static final Duration LEASE = Duration.ofSeconds(45);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(20);
    private static final int BATCH_SIZE = 25;

    private final WalletOutboxService outbox;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final String topic;
    private final Mono<String> indexReady;

    public WalletOutboxRelay(WalletOutboxService outbox, KafkaTemplate<String, String> kafka, JsonMapper json,
                             @Value("${wallet.outbox.topic:wallet.events.v1}") String topic) {
        if (topic.isBlank()) {
            throw new IllegalArgumentException("Wallet outbox topic is required");
        }
        this.outbox = outbox;
        this.kafka = kafka;
        this.json = json;
        this.topic = topic;
        this.indexReady = Mono.defer(outbox::ensurePublishIndex)
                .flatMap(WalletOutcome::value)
                .cache(value -> Duration.ofMillis(Long.MAX_VALUE), error -> Duration.ZERO, () -> Duration.ZERO);
    }

    @Scheduled(fixedDelayString = "${wallet.outbox.interval:2s}")
    public Mono<Void> publish() {
        return indexReady.thenMany(Flux.range(0, BATCH_SIZE)
                        .concatMap(ignored -> outbox.claim(LEASE).flatMap(WalletOutcome::optionalValue)
                                .flatMap(this::send).defaultIfEmpty(false))
                        .takeUntil(published -> !published))
                .then();
    }

    private Mono<Boolean> send(WalletOutboxModel event) {
        var payload = new WalletBalanceEventDtoV1(event.getId(), event.getWalletId(), event.getEventType(),
                event.getSchemaVersion(), event.getAmount(), event.getCurrency(), event.getResultingBalance(),
                event.getOccurredAt());
        // Kafka may wait for metadata in send(); isolate that wait in this background worker only.
        return Mono.fromFuture(() -> kafka.send(topic, event.getWalletId(), json.writeValueAsString(payload)), true)
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(SEND_TIMEOUT)
                .then(Mono.defer(() -> outbox.acknowledge(event)))
                .flatMap(WalletOutcome::value)
                .doOnNext(acknowledged -> {
                    if (!acknowledged) {
                        log.warn("Outbox acknowledgment lost its lease: eventId={}", event.getId());
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Outbox publish did not complete: eventId={} errorType={}",
                            event.getId(), error.getClass().getSimpleName());
                    return Mono.just(false);
                });
    }
}