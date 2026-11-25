package com.playtomic.tests.wallet.mappers;

import com.playtomic.tests.common.contracts.mappers.IModelToDtoMapper;
import com.playtomic.tests.wallet.dtos.WalletResponseDtoV1;
import com.playtomic.tests.wallet.models.WalletModel;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WalletResponseMapperV1Test {
    private final IModelToDtoMapper<WalletModel, WalletResponseDtoV1> mapper =
            Mappers.getMapper(WalletResponseMapperV1.class);

    @Test
    void mapsToDtoAndBackWithoutLosingPrecision() {
        var model = new WalletModel("wallet-1", new BigDecimal("9007199254740993.01"), "EUR");

        StepVerifier.create(mapper.toDtoMono(model)
                        .doOnNext(dto -> {
                            assertThat(dto.getId()).isEqualTo(model.getId());
                            assertThat(dto.getBalance()).isEqualTo(model.getBalance());
                            assertThat(dto.getCurrency()).isEqualTo(model.getCurrency());
                        })
                        .flatMap(mapper::toModelMono))
                .expectNext(model)
                .verifyComplete();
    }

    @Test
    void reportsModelValidationThroughTheReactiveErrorChannel() {
        var dto = new WalletResponseDtoV1(" ", BigDecimal.ONE, "EUR");

        StepVerifier.create(mapper.toModelMono(dto))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && "Wallet ID is required".equals(error.getMessage()))
                .verify();
    }
}

