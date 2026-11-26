package com.playtomic.tests.common.contracts.mappers;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ModelToDtoMapperTest {
    @Test
    void rejectsNullInputOnSubscription() {
        IErrorModelMapperV1 mapper = code -> {
            throw new AssertionError("Null must be rejected before error-code lookup");
        };
        StepVerifier.create(mapper.toModelMono(null))
                .expectErrorMessage("dto")
                .verify();
        StepVerifier.create(mapper.toDtoMono(null))
                .expectErrorMessage("model")
                .verify();
    }
}