package com.playtomic.tests.wallet.services;

import com.playtomic.tests.common.contracts.models.IEntityService;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.mappers.WalletEntityMapper;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.repositories.WalletRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.playtomic.tests.wallet.enums.WalletErrorCode.BALANCE_UPDATE_NOT_MATCHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalletServiceTest {
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final BigDecimal DELTA = new BigDecimal("-2.00");
    private static final Optional<BigDecimal> MINIMUM = Optional.of(DELTA.negate());
    private final WalletRepository repository = mock(WalletRepository.class);
    private final WalletService service = new WalletService(repository, Mappers.getMapper(WalletEntityMapper.class));

    @Test
    void matchedBalanceUpdateReturnsTheMappedWallet() {
        var entity = new WalletEntity("wallet", new BigDecimal("12.34"), "EUR");
        when(repository.incrementBalance("wallet", "EUR", DELTA, MINIMUM)).thenReturn(Mono.just(entity));

        StepVerifier.create(service.incrementBalance("wallet", "EUR", DELTA, MINIMUM))
                .assertNext(outcome -> {
                    assertThat(outcome.getData()).isEqualTo(new WalletModel("wallet", entity.getBalance(), "EUR"));
                    assertThat(outcome.getError()).isNull();
                })
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void unmatchedBalanceUpdateReturnsATypedFailure() {
        when(repository.incrementBalance("wallet", "EUR", DELTA, MINIMUM)).thenReturn(Mono.empty());

        StepVerifier.create(service.incrementBalance("wallet", "EUR", DELTA, MINIMUM))
                .assertNext(outcome -> {
                    assertThat(outcome.isFailure()).isTrue();
                    assertThat(outcome.getData()).isNull();
                    assertThat(outcome.getError().getCode()).isEqualTo(BALANCE_UPDATE_NOT_MATCHED);
                })
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void balanceUpdatePropagatesTheOriginalStorageError() {
        var failure = new DataAccessResourceFailureException("storage unavailable");
        when(repository.incrementBalance("wallet", "EUR", DELTA, MINIMUM)).thenReturn(Mono.error(failure));

        StepVerifier.create(service.incrementBalance("wallet", "EUR", DELTA, MINIMUM))
                .expectErrorMatches(actual -> actual == failure).verify(DEADLINE);
    }

    @Test
    void balanceUpdateDoesNotTreatMappingFailuresAsGuardMisses() {
        var entity = new WalletEntity("wallet", new BigDecimal("12.34"), "EUR");
        var mapper = mock(WalletEntityMapper.class);
        var guardedService = new WalletService(repository, mapper);
        var failure = new IllegalArgumentException("invalid wallet data");
        when(repository.incrementBalance("wallet", "EUR", DELTA, MINIMUM)).thenReturn(Mono.just(entity));
        when(mapper.toMonoModel(entity)).thenReturn(Mono.error(failure)).thenReturn(Mono.empty());

        StepVerifier.create(guardedService.incrementBalance("wallet", "EUR", DELTA, MINIMUM))
                .expectErrorMatches(actual -> actual == failure).verify(DEADLINE);
        StepVerifier.create(guardedService.incrementBalance("wallet", "EUR", DELTA, MINIMUM))
                .expectErrorMessage("Updated wallet could not be mapped").verify(DEADLINE);
    }

    @Test
    void sharedLookupUsesTheTypedMapper() {
        IEntityService<WalletEntity, WalletModel, String> sharedService = service;
        var entity = new WalletEntity("wallet", new BigDecimal("12.34"), "EUR");
        when(repository.findById("wallet")).thenReturn(Mono.just(entity));

        StepVerifier.create(sharedService.findById("wallet"))
                .expectNext(new WalletModel("wallet", entity.getBalance(), "EUR"))
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void sharedWritesCannotBypassDedicatedCommands() {
        var model = new WalletModel("wallet", BigDecimal.ZERO, "EUR");

        StepVerifier.create(service.update(model)).expectError(UnsupportedOperationException.class).verify(DEADLINE);
        StepVerifier.create(service.saveAllAsCollection(List.of(model)))
                .expectError(UnsupportedOperationException.class).verify(DEADLINE);
        StepVerifier.create(service.deleteById(model.getId()))
                .expectError(UnsupportedOperationException.class).verify(DEADLINE);
        verifyNoInteractions(repository);
    }
}