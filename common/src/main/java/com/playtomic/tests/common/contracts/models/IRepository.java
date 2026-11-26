package com.playtomic.tests.common.contracts.models;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

/** Shared repository contract for reactive entity persistence. */
@NoRepositoryBean
public interface IRepository<T extends IEntity<I>, I extends Serializable>
        extends ReactiveMongoRepository<T, I> {
}
