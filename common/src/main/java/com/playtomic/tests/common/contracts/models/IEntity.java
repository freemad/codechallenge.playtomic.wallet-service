package com.playtomic.tests.common.contracts.models;

import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;

public interface IEntity<I extends Serializable>
        extends Persistable<I>, Serializable{

    @Override
    I getId();

    /**
     * Marks the entity as new even if its ID has already been assigned.
     */
    @Transient
    boolean isNewRecord();

    /**
     * Programmatic hook to flip the insertion tracking state flag.
     */
    void setNewRecord(boolean isNew);

    @Override
    @Transient
    default boolean isNew() {
        return isNewRecord() || getId() == null;
    }
}