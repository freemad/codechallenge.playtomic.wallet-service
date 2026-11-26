package com.playtomic.tests.common.dtos;

import com.playtomic.tests.common.contracts.dtos.IDto;
import com.playtomic.tests.common.contracts.dtos.IResponseDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.OffsetDateTime;
import java.util.Map;

/** Version-one HTTP response envelope for a result or error. */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ResponseDtoV1
        implements IResponseDto {
    @Serial
    private static final long serialVersionUID = 2306084713327605194L;
    private final String type = "response";
    private final String version = "1";
    private String timestamp = OffsetDateTime.now().toString();

    private String cid;
    private IDto result;
    private ErrorDtoV1 error;
    private Map<String, String> metadata;

    /** Reports whether the result represents success rather than an error. */
    public boolean isSuccess() {
        return error == null;
    }
}