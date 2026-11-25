package com.playtomic.tests.wallet.configs;

import com.playtomic.tests.wallet.jobs.WalletReconciliationJob;
import com.playtomic.tests.wallet.messaging.WalletOutboxRelay;
import com.playtomic.tests.wallet.services.WalletOperationService;
import com.playtomic.tests.wallet.services.entity.WalletJobLeaseEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOperationEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOutboxEntityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletWorkerPropsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(WorkerPropertiesConfiguration.class,
                    WalletReconciliationJob.class, WalletOutboxRelay.class);

    @Test
    void bindsYamlDefaultsWithoutStartingWorkers() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(WalletReconciliationJob.class)
                    .doesNotHaveBean(WalletOutboxRelay.class);
            var reconciliation = context.getBean(WalletReconciliationProps.class);
            assertThat(reconciliation.interval()).isEqualTo(Duration.ofSeconds(15));
            assertThat(reconciliation.lease()).isEqualTo(Duration.ofMinutes(1));
            assertThat(reconciliation.runTimeout()).isEqualTo(Duration.ofSeconds(25));
            assertThat(reconciliation.stalePendingAge()).isEqualTo(Duration.ofMinutes(2));
            assertThat(reconciliation.receiptDelay()).isEqualTo(Duration.ofSeconds(15));
            assertThat(reconciliation.batchSize()).isEqualTo(25);
            var outbox = context.getBean(WalletOutboxProps.class);
            assertThat(outbox.interval()).isEqualTo(Duration.ofSeconds(2));
            assertThat(outbox.topic()).isEqualTo("wallet.events.v1");
            assertThat(outbox.claimLease()).isEqualTo(Duration.ofSeconds(45));
            assertThat(outbox.sendTimeout()).isEqualTo(Duration.ofSeconds(20));
            assertThat(outbox.batchSize()).isEqualTo(25);
        });
    }

    @Test
    void bindsOverridesAndRegistersTheConfiguredFixedDelays() {
        var leases = mock(WalletJobLeaseEntityService.class);
        when(leases.acquire(anyString(), any())).thenReturn(Mono.empty());
        runner.withBean(TaskScheduler.class, () -> mock(TaskScheduler.class))
                .withBean(WalletJobLeaseEntityService.class, () -> leases)
                .withBean(WalletOperationService.class, () -> mock(WalletOperationService.class))
                .withBean(WalletOperationEntityService.class, () -> mock(WalletOperationEntityService.class))
                .withBean(WalletOutboxEntityService.class, () -> mock(WalletOutboxEntityService.class))
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withPropertyValues("wallet.reconciliation.enabled=true", "wallet.outbox.enabled=true",
                        "wallet.reconciliation.interval=4s", "WALLET_OUTBOX_INTERVAL=250ms",
                        "WALLET_OUTBOX_TOPIC=wallet-test", "wallet.outbox.batch-size=3")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WalletReconciliationJob.class)
                            .hasSingleBean(WalletOutboxRelay.class);
                    var reconciliation = context.getBean(WalletReconciliationProps.class);
                    var outbox = context.getBean(WalletOutboxProps.class);
                    assertThat(reconciliation.interval()).isEqualTo(Duration.ofSeconds(4));
                    assertThat(outbox.interval()).isEqualTo(Duration.ofMillis(250));
                    assertThat(outbox.topic()).isEqualTo("wallet-test");
                    assertThat(outbox.batchSize()).isEqualTo(3);
                    var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
                    assertThat(tasks).extracting(task -> ((FixedDelayTask) task.getTask()).getIntervalDuration())
                            .containsExactlyInAnyOrder(Duration.ofSeconds(4), Duration.ofMillis(250));
                });
    }

    @ParameterizedTest
    @CsvSource({
            "wallet.reconciliation.interval=0s, wallet.reconciliation.interval must be positive",
            "wallet.reconciliation.lease=25s, wallet.reconciliation.lease must be longer than run-timeout",
            "wallet.outbox.claim-lease=20s, wallet.outbox.claim-lease must be longer than send-timeout to allow acknowledgment",
            "wallet.outbox.batch-size=0, wallet.outbox.batch-size must be positive",
            "wallet.outbox.topic=, wallet.outbox.topic must not be blank"
    })
    void rejectsInvalidSettingsEvenWhenWorkersAreDisabled(String property, String message) {
        runner.withPropertyValues(property).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(message);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({WalletReconciliationProps.class, WalletOutboxProps.class})
    static class WorkerPropertiesConfiguration {
    }
}