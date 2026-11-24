package com.playtomic.tests.common.dtos;

import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.util.List;

@Data
@Accessors(chain = true)
public class CollectionResultDtoV1<T> implements IDto {

    @Serial
    private static final long serialVersionUID = -5357787091226382317L;
    private final String type;
    private final String version;

    private List<T> items;

    /**
     * Explicit constructor enforces structural layout settings down to fields block
     */
    public CollectionResultDtoV1(String underlyingType, String structuralVersion, List<T> items) {
        // Appends standard array collection naming conventions cleanly to type references
        this.type = underlyingType != null ? underlyingType + "-list" : "generic-collection-list";
        this.version = structuralVersion != null ? structuralVersion : "1";
        this.items = items;
    }
}
