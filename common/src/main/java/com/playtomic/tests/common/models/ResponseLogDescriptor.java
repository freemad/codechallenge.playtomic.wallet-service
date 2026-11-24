package com.playtomic.tests.common.models;

import com.playtomic.tests.common.contracts.models.ILogDescriptor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ResponseLogDescriptor
        implements ILogDescriptor {
    @Serial
    private static final long serialVersionUID = 5294184395793105511L;

    Object object;
    String methodName;
    String timestamp;
    String caller;
    String xCaller;
    Long durationInMillis;
}
