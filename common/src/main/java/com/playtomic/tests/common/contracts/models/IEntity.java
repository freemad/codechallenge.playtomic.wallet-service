package com.playtomic.tests.common.contracts.models;

import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;

/** Supplies identity and new-record detection for Spring Data persistence. */
public interface IEntity<I extends Serializable>
        extends Persistable<I>, Serializable{

    /** Returns the identifier used to address this record. */
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

    /** Treats an explicitly new entity or one without an identifier as new. */
    @Override
    @Transient
    default boolean isNew() {
        return isNewRecord() || getId() == null;
    }
}