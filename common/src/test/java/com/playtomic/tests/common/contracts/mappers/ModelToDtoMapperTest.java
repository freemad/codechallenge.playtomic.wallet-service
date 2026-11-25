package com.playtomic.tests.common.contracts.mappers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

class ModelToDtoMapperTest {
    @ParameterizedTest
    @MethodSource("mappers")
    void rejectsNullInputOnSubscription(IModelToDtoMapper<?, ?> mapper) {
        StepVerifier.create(mapper.toModelMono(null))
                .expectErrorMessage("dto")
                .verify();
        StepVerifier.create(mapper.toDtoMono(null))
                .expectErrorMessage("model")
                .verify();
    }

    private static Stream<IModelToDtoMapper<?, ?>> mappers() {
        IErrorModelMapperV1 errorMapper = code -> {
            throw new AssertionError("Null must be rejected before error-code lookup");
        };
        return Stream.of(Mappers.getMapper(IPaymentReviewMapperV1.class),
                Mappers.getMapper(IPaymentReviewCaseMapperV1.class), errorMapper);
    }
}
