package com.playtomic.tests.wallet.services.entity;

import com.playtomic.tests.common.contracts.models.IEntityService;
import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.entities.WalletEntity;
import com.playtomic.tests.wallet.mappers.WalletEntityMapper;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.repositories.IWalletBalanceMongoOperations;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalletServiceTest {
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final BigDecimal DELTA = new BigDecimal("-2.00");
    private static final Optional<BigDecimal> MINIMUM = Optional.of(DELTA.negate());
    private static final WalletModel SNAPSHOT = new WalletModel("wallet", new BigDecimal("14.34"), "EUR");
    private final WalletRepository repository = mock(WalletRepository.class);
    private final IWalletBalanceMongoOperations balanceOperations = mock(IWalletBalanceMongoOperations.class);
    private final WalletEntityService service = new WalletEntityService(repository, Mappers.getMapper(WalletEntityMapper.class),
            balanceOperations);

    @Test
    void matchedBalanceUpdateReturnsTheWalletModel() {
        var entity = new WalletEntity("wallet", new BigDecimal("12.34"), "EUR");
        when(balanceOperations.incrementBalance("wallet", "EUR", DELTA, MINIMUM)).thenReturn(Mono.just(entity));

        StepVerifier.create(service.incrementBalance(SNAPSHOT, DELTA, MINIMUM))
                .expectNext(new WalletModel("wallet", entity.getBalance(), "EUR"))
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void unmatchedBalanceUpdateCompletesWithoutAModel() {
        when(balanceOperations.incrementBalance("wallet", "EUR", DELTA, MINIMUM)).thenReturn(Mono.empty());

        StepVerifier.create(service.incrementBalance(SNAPSHOT, DELTA, MINIMUM))
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void unmappablePostImageIsNotMistakenForAGuardMiss() {
        var mapper = mock(WalletEntityMapper.class);
        var entity = new WalletEntity("wallet", BigDecimal.ONE, "EUR");
        var walletEntities = new WalletEntityService(repository, mapper, balanceOperations);
        when(balanceOperations.incrementBalance("wallet", "EUR", DELTA, MINIMUM)).thenReturn(Mono.just(entity));
        when(mapper.toMonoModel(entity)).thenReturn(Mono.empty());

        StepVerifier.create(walletEntities.incrementBalance(SNAPSHOT, DELTA, MINIMUM))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(BusinessException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(CommonErrorCode.ILLEGAL_STATE_EXCEPTION);
                    assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class)
                            .hasMessage("Updated wallet could not be mapped");
                })).verify(DEADLINE);
    }

    @Test
    void balanceUpdatePropagatesTheOriginalStorageError() {
        var failure = new DataAccessResourceFailureException("storage unavailable");
        when(balanceOperations.incrementBalance("wallet", "EUR", DELTA, MINIMUM)).thenReturn(Mono.error(failure));

        StepVerifier.create(service.incrementBalance(SNAPSHOT, DELTA, MINIMUM))
                .expectErrorMatches(actual -> actual == failure).verify(DEADLINE);
    }

    @Test
    void inexactProspectiveBalanceFailsBeforeWriting() {
        var snapshot = new WalletModel("wallet", new BigDecimal("1E+34"), "EUR");

        StepVerifier.create(service.incrementBalance(snapshot, BigDecimal.ONE, Optional.empty()))
                .expectError(NumberFormatException.class).verify(DEADLINE);
        verifyNoInteractions(balanceOperations);
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
        IEntityService<WalletEntity, WalletModel, String> shared = service;
        StepVerifier.create(shared.saveAllAsCollection(reactor.core.publisher.Flux.just(model)))
                .expectError(UnsupportedOperationException.class).verify(DEADLINE);
        StepVerifier.create(service.deleteById(model.getId()))
                .expectError(UnsupportedOperationException.class).verify(DEADLINE);
        verifyNoInteractions(repository, balanceOperations);
    }
}