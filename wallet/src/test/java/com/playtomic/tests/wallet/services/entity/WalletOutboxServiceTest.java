package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.entities.WalletOutboxEntity;
import com.playtomic.tests.wallet.enums.WalletEventType;
import com.playtomic.tests.wallet.mappers.WalletOutboxEntityMapper;
import com.playtomic.tests.wallet.models.WalletOutboxModel;
import com.playtomic.tests.wallet.repositories.IWalletOutboxMongoOperations;
import com.playtomic.tests.wallet.repositories.WalletOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletOutboxServiceTest {
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private final WalletOutboxRepository repository = mock(WalletOutboxRepository.class);
    private final WalletOutboxEntityMapper mapper = mock(WalletOutboxEntityMapper.class);
    private final IWalletOutboxMongoOperations operations = mock(IWalletOutboxMongoOperations.class);
    private final WalletOutboxEntityService service = new WalletOutboxEntityService(repository, mapper, operations);
    private final WalletOutboxModel event = new WalletOutboxModel("event", "wallet", WalletEventType.WALLET_CREDITED,
            1, BigDecimal.ONE, "EUR", BigDecimal.TEN, NOW, NOW, "lease", null);
    private final WalletOutboxEntity entity = new WalletOutboxEntity("event", "wallet", "WalletCredited",
            1, BigDecimal.ONE, "EUR", BigDecimal.TEN, NOW, NOW, "lease", null);

    @Test
    void normalizesAnEmptyInsert() {
        when(mapper.toMonoEntity(event)).thenReturn(Mono.just(entity));
        when(repository.insert(entity)).thenReturn(Mono.empty());
        verifyInvariant(service.enqueue(event), "Outbox event was not inserted");
    }

    @Test
    void preservesTheInsertFailureForTheTransactionOwner() {
        var failure = new DuplicateKeyException("duplicate event");
        when(mapper.toMonoEntity(event)).thenReturn(Mono.just(entity));
        when(repository.insert(entity)).thenReturn(Mono.error(failure));
        StepVerifier.create(service.enqueue(event)).expectErrorMatches(error -> error == failure).verify(DEADLINE);
    }

    @Test
    void normalizesAnEmptyIndexResult() {
        when(operations.ensurePublishIndex()).thenReturn(Mono.empty());
        verifyInvariant(service.ensurePublishIndex(), "Outbox index was not created");
    }

    @Test
    void distinguishesAnUnmappableClaimFromNoClaim() {
        when(operations.claim(any(), any(), anyString())).thenReturn(Mono.just(entity)).thenReturn(Mono.empty());
        when(mapper.toMonoModel(entity)).thenReturn(Mono.empty());
        verifyInvariant(service.claim(DEADLINE), "Claimed event could not be mapped");
        StepVerifier.create(service.claim(DEADLINE)).expectComplete().verify(DEADLINE);
    }

    @Test
    void distinguishesAMissingAcknowledgmentResultFromAStaleLease() {
        when(repository.acknowledge(eq("event"), eq("lease"), any()))
                .thenReturn(Mono.empty()).thenReturn(Mono.just(0L));
        verifyInvariant(service.acknowledge(event), "Outbox acknowledgment returned no result");
        StepVerifier.create(service.acknowledge(event)).expectNext(false).expectComplete().verify(DEADLINE);
    }

    private static void verifyInvariant(Mono<?> result, String message) {
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(BusinessException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(CommonErrorCode.ILLEGAL_STATE_EXCEPTION);
                    assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class).hasMessage(message);
                })).verify(DEADLINE);
    }
}