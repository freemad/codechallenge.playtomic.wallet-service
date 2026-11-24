package com.playtomic.tests.common.contracts.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public interface ITyped extends Serializable {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String getType();
}
