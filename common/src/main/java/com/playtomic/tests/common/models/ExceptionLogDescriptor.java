package com.playtomic.tests.common.models;

import com.playtomic.tests.common.contracts.models.ILogDescriptor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ExceptionLogDescriptor
        implements ILogDescriptor {
    @Serial
    private static final long serialVersionUID = 6115939467820833026L;

    Object object;
    String methodName;
    String timestamp;
    String caller;
    String xCaller;
    List<String> exceptions;
}
