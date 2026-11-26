package com.playtomic.tests.common.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.playtomic.tests.common.contracts.dtos.IErrorDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.util.Map;

/** Version-one transport representation of an application failure. */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class ErrorDtoV1
        implements IErrorDto {
    @Serial
    private static final long serialVersionUID = 3869019813046821811L;
    private final String type = "error";
    private final String version = "1";

    private Integer code;
    private String message;
    private Map<String, String> params;
}
