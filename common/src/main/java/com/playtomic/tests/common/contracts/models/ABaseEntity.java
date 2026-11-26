package com.playtomic.tests.common.contracts.models;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;
import java.io.Serial;
import java.io.Serializable;

/** Tracks the explicit new-record flag used by Spring Data when an identifier is assigned before insertion. */
@Accessors(chain = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ABaseEntity<I extends Serializable>
        implements IEntity<I> {
    @Serial
    private static final long serialVersionUID = -1420254183019774522L;

    @Transient
    private boolean isNewRecord = true; // Defaults safely to true for application instantiations

    /** Reports whether persistence should treat the entity as explicitly new. */
    @Override
    @Transient
    public boolean isNewRecord() {
        return this.isNewRecord;
    }

    /** Sets the explicit new-record flag independently of the assigned identifier. */
    @Override
    public void setNewRecord(boolean isNew) {
        this.isNewRecord = isNew;
    }
}
