package com.playtomic.tests.wallet.services;

import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Providers enabled for fresh wallet top-ups, not a live health probe. */
public final class WalletTopUpProviders {
    private final Map<String, Function<CardChargeModel, Mono<PaymentReceiptModel>>> providers;

    public WalletTopUpProviders(Map<String, Function<CardChargeModel, Mono<PaymentReceiptModel>>> providers) {
        this.providers = Map.copyOf(providers);
    }

    public List<String> activeIds() {
        return providers.keySet().stream().sorted().toList();
    }

    public boolean isActive(String id) {
        return providers.containsKey(id);
    }

    Mono<PaymentReceiptModel> charge(String id, CardChargeModel charge) {
        return providers.get(id).apply(charge);
    }
}