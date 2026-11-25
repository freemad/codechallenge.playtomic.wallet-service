package com.playtomic.tests.wallet.messaging;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.configs.WalletOutboxProps;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.enums.WalletEventType;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.services.entity.WalletOutboxEntityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WalletOutboxRelayTest {
    private static final WalletOutboxProps SETTINGS = new WalletOutboxProps(
            Duration.ofSeconds(3), "wallet-test", Duration.ofSeconds(35), Duration.ofSeconds(8), 2);
    private final WalletOutboxEntityService outbox = mock(WalletOutboxEntityService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final WalletOutboxRelay relay = new WalletOutboxRelay(outbox, kafka, json, SETTINGS);
    private final WalletOutboxModel event = new WalletOutboxModel("event-1", "wallet-1",
            WalletEventType.WALLET_CREDITED, 2, new BigDecimal("9007199254740993.01"), "EUR",
            new BigDecimal("9007199254741000.01"), Instant.parse("2026-11-24T10:00:00Z"),
            Instant.parse("2026-11-24T10:00:45Z"), "lease-token", null);

    @BeforeEach
    void queueOneEvent() {
        when(outbox.ensurePublishIndex()).thenReturn(Mono.just("index"));
        when(outbox.claim(any(Duration.class)))
                .thenReturn(Mono.just(event))
                .thenReturn(Mono.empty());
    }

    @Test
    void sendsStableEventPayloadAndAcknowledgesOnlyAfterBrokerSuccess() {
        var sent = new CompletableFuture<SendResult<String, String>>();
        var sendStarted = new CompletableFuture<String>();
        when(kafka.send(eq("wallet-test"), eq("wallet-1"), anyString())).thenAnswer(call -> {
            sendStarted.complete(call.getArgument(2, String.class));
            return sent;
        });
        when(outbox.acknowledge(event)).thenReturn(Mono.just(true));

        StepVerifier.create(relay.publish()).then(() -> {
            var payload = json.readTree(sendStarted.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join());
            assertThat(payload.path("eventId").asString()).isEqualTo("event-1");
            assertThat(payload.path("eventType").asString()).isEqualTo("WalletCredited");
            assertThat(payload.path("schemaVersion").isIntegralNumber()).isTrue();
            assertThat(payload.path("schemaVersion").asInt()).isEqualTo(2);
            assertThat(payload.path("amount").decimalValue()).isEqualByComparingTo(event.getAmount());
            assertThat(payload.size()).isEqualTo(8);
            verify(outbox, never()).acknowledge(any());
            sent.complete(mock(SendResult.class));
        }).expectComplete().verify(Duration.ofSeconds(10));
        verify(outbox).acknowledge(event);
        verify(outbox, times(2)).claim(any(Duration.class));
    }

    @Test
    void limitsClaimsToTheConfiguredBatchSize() {
        when(outbox.claim(SETTINGS.claimLease())).thenReturn(Mono.just(event));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(outbox.acknowledge(event)).thenReturn(Mono.just(true));

        StepVerifier.create(relay.publish()).expectComplete().verify(Duration.ofSeconds(5));

        verify(outbox, times(2)).claim(SETTINGS.claimLease());
        verify(outbox, times(2)).acknowledge(event);
    }

    @Test
    void doesNotAcknowledgeABrokerFailure() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker failed")));
        StepVerifier.create(relay.publish()).expectComplete().verify(Duration.ofSeconds(5));
        verify(outbox, never()).acknowledge(any());
    }

    @Test
    void stopsOnAnEmptyClaimWithoutSending() {
        when(outbox.claim(any(Duration.class))).thenReturn(Mono.empty());

        StepVerifier.create(relay.publish()).expectComplete().verify(Duration.ofSeconds(5));

        verify(outbox).claim(any(Duration.class));
        verify(outbox, never()).acknowledge(any());
        verifyNoInteractions(kafka);
    }

    @Test
    void stopsAfterAnAcknowledgmentLosesItsLease() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(outbox.acknowledge(event)).thenReturn(Mono.just(false));

        StepVerifier.create(relay.publish()).expectComplete().verify(Duration.ofSeconds(5));

        verify(outbox).acknowledge(event);
        verify(outbox).claim(any(Duration.class));
    }

    @Test
    void normalizesClaimFailuresWithoutSending() {
        var failure = new DataAccessResourceFailureException("claim failed");
        when(outbox.claim(any(Duration.class))).thenReturn(Mono.error(failure));

        StepVerifier.create(relay.publish())
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(BusinessException.class, business -> {
                    assertThat(business.getErrorCode()).isEqualTo(CommonErrorCode.DATA_RETRIEVE_EXCEPTION);
                    assertThat(business.getCause()).isSameAs(failure);
                })).verify(Duration.ofSeconds(5));
        verifyNoInteractions(kafka);
    }

    @Test
    void doesNotRecoverACanceledSendOrAcknowledgeIt() {
        var failure = new CancellationException("send canceled");
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        StepVerifier.create(relay.publish()).expectErrorMatches(error -> error == failure)
                .verify(Duration.ofSeconds(5));
        verify(outbox, never()).acknowledge(any());
    }

    @Test
    void doesNotCacheAnIndexFailureAsSuccess() {
        var failure = new BusinessException(WalletErrorCode.INVALID_REQUEST, "index failed");
        when(outbox.ensurePublishIndex())
                .thenReturn(Mono.error(failure))
                .thenReturn(Mono.just("index"));
        when(outbox.claim(any(Duration.class))).thenReturn(Mono.empty());

        StepVerifier.create(relay.publish()).expectErrorMatches(error -> error == failure)
                .verify(Duration.ofSeconds(5));
        verify(outbox, never()).claim(any(Duration.class));

        StepVerifier.create(relay.publish()).expectComplete().verify(Duration.ofSeconds(5));
        verify(outbox, times(2)).ensurePublishIndex();
        verify(outbox).claim(any(Duration.class));
        verifyNoInteractions(kafka);
    }
}