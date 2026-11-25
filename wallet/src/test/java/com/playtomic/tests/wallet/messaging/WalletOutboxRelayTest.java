package com.playtomic.tests.wallet.messaging;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.services.WalletOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WalletOutboxRelayTest {
    private final WalletOutboxService outbox = mock(WalletOutboxService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final WalletOutboxRelay relay = new WalletOutboxRelay(outbox, kafka, json, "wallet-test");
    private final WalletOutboxModel event = new WalletOutboxModel("event-1", "wallet-1", "WalletCredited", 1,
            new BigDecimal("9007199254740993.01"), "EUR", new BigDecimal("9007199254741000.01"),
            Instant.parse("2026-11-24T10:00:00Z"), Instant.parse("2026-11-24T10:00:45Z"), "lease-token", null);

    @BeforeEach
    void queueOneEvent() {
        when(outbox.ensurePublishIndex()).thenReturn(Mono.just(Outcome.success("index")));
        when(outbox.claim(any(Duration.class)))
                .thenReturn(Mono.just(Outcome.success(Optional.of(event))))
                .thenReturn(Mono.just(Outcome.success(Optional.empty())));
    }

    @Test
    void sendsStableEventPayloadAndAcknowledgesOnlyAfterBrokerSuccess() {
        var sent = new CompletableFuture<SendResult<String, String>>();
        var sendStarted = new CompletableFuture<String>();
        when(kafka.send(eq("wallet-test"), eq("wallet-1"), anyString())).thenAnswer(call -> {
            sendStarted.complete(call.getArgument(2, String.class));
            return sent;
        });
        when(outbox.acknowledge(event)).thenReturn(Mono.just(Outcome.success(true)));

        StepVerifier.create(relay.publish()).then(() -> {
            var payload = json.readTree(sendStarted.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join());
            assertThat(payload.path("eventId").asString()).isEqualTo("event-1");
            assertThat(payload.path("eventType").asString()).isEqualTo("WalletCredited");
            assertThat(payload.path("amount").decimalValue()).isEqualByComparingTo(event.getAmount());
            assertThat(payload.size()).isEqualTo(8);
            verify(outbox, never()).acknowledge(any());
            sent.complete(mock(SendResult.class));
        }).expectComplete().verify(Duration.ofSeconds(10));
        verify(outbox).acknowledge(event);
        verify(outbox, times(2)).claim(any(Duration.class));
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
        when(outbox.claim(any(Duration.class))).thenReturn(Mono.just(Outcome.success(Optional.empty())));

        StepVerifier.create(relay.publish()).expectComplete().verify(Duration.ofSeconds(5));

        verify(outbox).claim(any(Duration.class));
        verify(outbox, never()).acknowledge(any());
        verifyNoInteractions(kafka);
    }

    @Test
    void stopsAfterAnAcknowledgmentLosesItsLease() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(outbox.acknowledge(event)).thenReturn(Mono.just(Outcome.success(false)));

        StepVerifier.create(relay.publish()).expectComplete().verify(Duration.ofSeconds(5));

        verify(outbox).acknowledge(event);
        verify(outbox).claim(any(Duration.class));
    }

    @Test
    void doesNotCacheAnIndexFailureAsSuccess() {
        var failure = new BusinessException(WalletErrorCode.INVALID_REQUEST, "index failed");
        when(outbox.ensurePublishIndex())
                .thenReturn(Mono.just(Outcome.failure(new Error().setException(failure))))
                .thenReturn(Mono.just(Outcome.success("index")));
        when(outbox.claim(any(Duration.class))).thenReturn(Mono.just(Outcome.success(Optional.empty())));

        StepVerifier.create(relay.publish()).expectErrorMatches(error -> error == failure)
                .verify(Duration.ofSeconds(5));
        verify(outbox, never()).claim(any(Duration.class));

        StepVerifier.create(relay.publish()).expectComplete().verify(Duration.ofSeconds(5));
        verify(outbox, times(2)).ensurePublishIndex();
        verify(outbox).claim(any(Duration.class));
        verifyNoInteractions(kafka);
    }
}