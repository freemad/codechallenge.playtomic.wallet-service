package com.playtomic.tests.wallet.messaging;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.configs.WalletOutboxProps;
import com.playtomic.tests.wallet.constants.WalletWorkerParam;
import com.playtomic.tests.wallet.dtos.WalletBalanceEventDtoV1;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.services.entity.WalletOutboxEntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = WalletOutboxProps.PREFIX, name = "enabled", havingValue = "true")
public class WalletOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(WalletOutboxRelay.class);

    private final WalletOutboxEntityService outbox;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final WalletOutboxProps properties;
    private final Mono<String> indexReady;

    public WalletOutboxRelay(WalletOutboxEntityService outbox, KafkaTemplate<String, String> kafka, JsonMapper json,
                             WalletOutboxProps properties) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.json = json;
        this.properties = properties;
        this.indexReady = WalletWorkerParam.cacheSuccessfulIndex(Mono.defer(outbox::ensurePublishIndex));
    }

    @Scheduled(fixedDelayString = "${" + WalletOutboxProps.PREFIX + ".interval}")
    public Mono<Void> publish() {
        return indexReady.thenMany(Flux.range(0, properties.batchSize())
                        .concatMap(ignored -> outbox.claim(properties.claimLease())
                                .flatMap(this::send).defaultIfEmpty(false))
                        .takeUntil(published -> !published))
                .then()
                .onErrorMap(ExceptionNormalizer::normalize);
    }

    private Mono<Boolean> send(WalletOutboxModel event) {
        // Kafka may wait for metadata in send(); isolate that wait in this background worker only.
        return Mono.fromFuture(() -> {
                    var payload = new WalletBalanceEventDtoV1(event.getId(), event.getWalletId(),
                            event.getEventType().getWireValue(), event.getSchemaVersion(), event.getAmount(),
                            event.getCurrency(), event.getResultingBalance(), event.getOccurredAt());
                    return kafka.send(properties.topic(), event.getWalletId(), json.writeValueAsString(payload));
                }, true)
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(properties.sendTimeout())
                .then(Mono.defer(() -> outbox.acknowledge(event)))
                .doOnNext(acknowledged -> {
                    if (!acknowledged) {
                        log.warn("Outbox acknowledgment lost its lease: eventId={}", event.getId());
                    }
                })
                .onErrorMap(ExceptionNormalizer::normalize)
                .onErrorResume(BusinessException.class, error -> {
                    log.warn("Outbox publish did not complete: eventId={} errorCode={}",
                            event.getId(), error.getErrorCode().getCode());
                    return Mono.just(false);
                });
    }
}