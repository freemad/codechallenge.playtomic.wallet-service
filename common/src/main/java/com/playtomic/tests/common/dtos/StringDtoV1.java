package com.playtomic.tests.common.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.playtomic.tests.common.contracts.dtos.IDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;

/** Wraps a string as a typed, versioned response result. */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class StringDtoV1
        implements IDto {
    @Serial
    private static final long serialVersionUID = -740327127776835224L;
    private final String type = "string";
    private final String version = "1";

    private String string;
}