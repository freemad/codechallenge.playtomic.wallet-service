package com.playtomic.tests.wallet.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Wire values for the PayPal and unsigned Redsys fixtures; never wallet operations. */
public interface FixtureWire {
    /** PayPal monetary amount in major units. */
    record Amount(@JsonProperty("currency_code") String currency, String value) {}
    /** One purchase unit requested from the fixture. */
    record Unit(Amount amount) {}
    /** Order creation request. */
    record Create(String intent, @JsonProperty("purchase_units") List<Unit> units) {}
    /** Order identity and approval state returned at creation. */
    record Created(String id, String status) {}
    /** Capture response projected from the one supported purchase unit. */
    record Capture(boolean unprocessable, String name, String issue, String orderId, String status,
                   String captureId, String captureStatus, Amount amount) {}
    /** Unsigned Redsys transport envelope. */
    record Envelope(@JsonProperty("Ds_MerchantParameters") String parameters, @JsonProperty("Ds_SignatureVersion") String version,
                    @JsonProperty("Ds_Signature") String signature) {}
    /** Redsys authorization request, before encoding. */
    record Authorization(@JsonProperty("DS_MERCHANT_ORDER") String order, @JsonProperty("DS_MERCHANT_AMOUNT") String amount,
                         @JsonProperty("DS_MERCHANT_CURRENCY") String currency, @JsonProperty("DS_MERCHANT_TRANSACTIONTYPE") String type) {}
}
