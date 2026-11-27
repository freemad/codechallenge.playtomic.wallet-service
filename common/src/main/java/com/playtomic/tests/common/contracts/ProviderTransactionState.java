package com.playtomic.tests.common.contracts;

import com.fasterxml.jackson.annotation.JsonValue;

/** Provider-specific status token on the HTTP wire; not a wallet outcome or a database code. */
public interface ProviderTransactionState {
    @JsonValue
    String wireValue();
}

