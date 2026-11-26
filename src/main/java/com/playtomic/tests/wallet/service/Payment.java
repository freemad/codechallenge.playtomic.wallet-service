package com.playtomic.tests.wallet.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;

/** Identifies a payment returned by the Stripe service. */
public class Payment {

    @NonNull
    private String id;

    /** Identifies the accepted provider payment. */
    @JsonCreator
    public Payment(@JsonProperty(value = "id", required = true) String id) {
        this.id = id;
    }
}
