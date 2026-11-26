package com.playtomic.tests.common.contracts.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/** Exposes the version of a transport representation. */
public interface IVersioned extends Serializable {
    /** Returns the version of the transport representation. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String getVersion();
}
