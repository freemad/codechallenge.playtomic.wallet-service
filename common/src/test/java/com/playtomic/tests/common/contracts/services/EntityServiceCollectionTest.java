package com.playtomic.tests.common.contracts.services;

import com.playtomic.tests.common.contracts.models.IEntity;
import com.playtomic.tests.common.contracts.models.IModel;
import com.playtomic.tests.common.contracts.models.IRepository;
import lombok.Data;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serial;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityServiceCollectionTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private final TestRepository repository = mock(TestRepository.class);

    @Test
    void collectsConvertedModelsForEachReadOverload() {
        var ids = List.of("a", "b");
        var idStream = Flux.fromIterable(ids);
        var entities = Flux.just(new TestEntity("a"), new TestEntity("b"));
        when(repository.findAll()).thenReturn(entities);
        when(repository.findAllById(ids)).thenReturn(entities);
        when(repository.findAllById(idStream)).thenReturn(entities);
        var service = service(entity -> Mono.just(new TestModel(entity.getId())));
        var expected = List.of(new TestModel("a"), new TestModel("b"));

        assertEquals(expected, service.findAllAsCollection().block(TIMEOUT));
        assertEquals(expected, service.findAllByIdAsCollection(ids).block(TIMEOUT));
        assertEquals(expected, service.findAllByIdAsCollection(idStream).block(TIMEOUT));
    }

    @Test
    void emitsAnEmptyCollectionWhenNoEntitiesAreFound() {
        when(repository.findAll()).thenReturn(Flux.empty());
        var service = service(entity -> Mono.error(new AssertionError("No entity should be converted")));

        assertEquals(List.of(), service.findAllAsCollection().block(TIMEOUT));
    }

    @Test
    void propagatesRepositoryAndConversionFailures() {
        var readFailure = new IllegalStateException("read failed");
        var mappingFailure = new IllegalArgumentException("mapping failed");
        var ids = List.of("a");
        when(repository.findAll()).thenReturn(Flux.error(readFailure));
        when(repository.findAllById(ids)).thenReturn(Flux.just(new TestEntity("a")));
        var service = service(entity -> Mono.error(mappingFailure));

        assertSame(readFailure, assertThrows(IllegalStateException.class,
                () -> service.findAllAsCollection().block(TIMEOUT)));
        assertSame(mappingFailure, assertThrows(IllegalArgumentException.class,
                () -> service.findAllByIdAsCollection(ids).block(TIMEOUT)));
    }

    private IEntityService<TestModel, TestEntity, String> service(Function<TestEntity, Mono<TestModel>> converter) {
        return new IEntityService<>() {
            @Override
            public Logger getLogger() {
                return LoggerFactory.getLogger(EntityServiceCollectionTest.class);
            }

            @Override
            public TestRepository getRepository() {
                return repository;
            }

            @Override
            public Function<TestEntity, Mono<TestModel>> getMonoModelConverter() {
                return converter;
            }

            @Override
            public Function<TestModel, Mono<TestEntity>> getMonoEntityConverter() {
                return model -> Mono.just(new TestEntity(model.getId()));
            }
        };
    }

    private interface TestRepository extends IRepository<TestEntity, String> {}

    @Data
    private static class TestEntity implements IEntity<String> {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String id;
        private boolean newRecord;
    }

    @Value
    private static class TestModel implements IModel {
        @Serial
        private static final long serialVersionUID = 1L;

        String id;
    }
}
