package com.playtomic.tests.wallet.messaging;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.configs.WalletOutboxProps;
import com.playtomic.tests.wallet.constants.WalletWorkerParam;
import com.playtomic.tests.wallet.dtos.WalletBalanceEventDtoV1;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.services.entity.WalletOutboxEntityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

import static com.playtomic.tests.wallet.observability.WalletLogEvent.*;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = WalletOutboxProps.PREFIX, name = "enabled", havingValue = "true")
public class WalletOutboxRelay {
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
        return indexReady.thenMany(Flux.range(0, properties.getBatchSize())
                        .concatMap(ignored -> outbox.claim(properties.getClaimLease())
                                .flatMap(this::send).defaultIfEmpty(false))
                        .takeUntil(published -> !published))
                .then()
                .onErrorMap(ExceptionNormalizer::normalize)
                .doOnError(error -> RELAY_FAILED.fields().log());
    }

    private Mono<Boolean> send(WalletOutboxModel event) {
        // Kafka may wait for metadata in send(); isolate that wait in this background worker only.
        return Mono.fromFuture(() -> {
                    var payload = new WalletBalanceEventDtoV1(event.getId(), event.getWalletId(),
                            event.getEventType().getWireValue(), event.getSchemaVersion(), event.getAmount(),
                            event.getCurrency(), event.getResultingBalance(), event.getOccurredAt());
                    return kafka.send(properties.getTopic(), event.getWalletId(), json.writeValueAsString(payload));
                }, true)
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(properties.getSendTimeout())
                .doOnNext(result -> BROKER_ACCEPTED.operation(event.getId()).log())
                .then(Mono.defer(() -> outbox.acknowledge(event)))
                .doOnNext(acknowledged -> (acknowledged ? ACKNOWLEDGED : LOST_LEASE).operation(event.getId()).log())
                .onErrorMap(ExceptionNormalizer::normalize)
                .onErrorResume(BusinessException.class, error -> {
                    DELIVERY_INCOMPLETE.operation(event.getId()).addKeyValue("errorCode", error.getErrorCode().getCode()).log();
                    return Mono.just(false);
                });
    }
}