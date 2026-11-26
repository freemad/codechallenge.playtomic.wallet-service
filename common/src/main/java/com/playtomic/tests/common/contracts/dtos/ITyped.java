package com.playtomic.tests.common.contracts.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/** Exposes the discriminator identifying a transport representation. */
public interface ITyped extends Serializable {
    /** Returns the transport type discriminator. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String getType();
}
