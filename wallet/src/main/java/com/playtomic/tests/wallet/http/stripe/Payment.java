package com.playtomic.tests.wallet.http.stripe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import lombok.experimental.Accessors;

/** Identifies a payment returned by the Stripe service. */
@Value
@Accessors(chain = true)
public class Payment {

    String id;

    /** Identifies the accepted provider payment. */
    @JsonCreator
    public Payment(@JsonProperty(value = "id", required = true) String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Provider payment ID is required");
        }
        this.id = id;
    }
}