package com.playtomic.tests.wallet.jobs;

import com.playtomic.tests.wallet.configs.WalletAuditProps;
import com.playtomic.tests.wallet.constants.WalletWorkerParam;
import com.playtomic.tests.wallet.repositories.WalletAuditMongoOperations;
import com.playtomic.tests.wallet.services.entity.WalletBalanceAuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.observability.WalletLogEvent;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "wallet.audit.enabled", havingValue = "true")
public class WalletBalanceAuditWorker {
    private final WalletBalanceAuditService audit;
    private final WalletAuditMongoOperations store;
    private final WalletAuditProps props;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final Mono<String> ready;

    public WalletBalanceAuditWorker(WalletBalanceAuditService audit, WalletAuditMongoOperations store, WalletAuditProps props,
                                    KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.audit = audit;
        this.store = store;
        this.props = props;
        this.kafka = kafka;
        this.json = json;
        ready = WalletWorkerParam.cacheSuccessfulIndex(Mono.defer(store::indexes));
    }

    @Scheduled(fixedDelayString = "${wallet.audit.interval}")
    public Mono<Void> calculate() {
        return batch(() -> audit.auditNext(props.getLease()), WalletLogEvent.AUDIT_FAILED);
    }

    @Scheduled(fixedDelayString = "${wallet.audit.interval}")
    public Mono<Void> publish() {
        return batch(() -> store.claimAlert(Instant.now(), props.getLease()).flatMap(row -> {
            String id = row.getString("_id");
            String payload = json.writeValueAsString(Map.of("eventId", id, "eventType", "WalletBalanceDiscrepancy", "schemaVersion", 1));
            return Mono.fromFuture(() -> kafka.send(props.getTopic(), id, payload), true).subscribeOn(Schedulers.boundedElastic())
                    .then(Mono.defer(() -> store.acknowledge(row))).thenReturn(true);
        }).defaultIfEmpty(false), WalletLogEvent.AUDIT_ALERT_FAILED);
    }

    private Mono<Void> batch(Supplier<Mono<Boolean>> work, WalletLogEvent failed) {
        return ready.thenMany(Flux.range(0, props.getBatchSize()).concatMap(i -> Mono.defer(work).timeout(props.getTimeout()))
                        .takeUntil(done -> !done)).then().onErrorMap(ExceptionNormalizer::normalize)
                .onErrorResume(BusinessException.class, e -> {
                    failed.fields().addKeyValue("errorCode", e.getErrorCode().getCode()).log();
                    return Mono.empty();
                });
    }
}
