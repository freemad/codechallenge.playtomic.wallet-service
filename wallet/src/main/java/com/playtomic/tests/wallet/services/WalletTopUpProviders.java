package com.playtomic.tests.wallet.services;

import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** Providers enabled for fresh wallet top-ups, not a live health probe. */
public final class WalletTopUpProviders {
    /** A single charge attempt through an explicitly enabled top-up provider. */
    @FunctionalInterface
    public interface TopUpChargeStrategy {
        Mono<PaymentReceiptModel> charge(CardChargeModel request);
    }

    private final Map<String, TopUpChargeStrategy> providers;

    /** Registers the adapters available for top-up dispatch. */
    public WalletTopUpProviders(Map<String, TopUpChargeStrategy> providers) {
        this.providers = Map.copyOf(providers);
    }

    /** Lists identifiers of configured top-up adapters, not live provider readiness. */
    public List<String> activeIds() {
        return providers.keySet().stream().sorted().toList();
    }

    /** Checks whether a top-up adapter is configured for the supplied identifier. */
    public boolean isActive(String id) {
        return providers.containsKey(id);
    }

    Mono<PaymentReceiptModel> charge(String id, CardChargeModel charge) {
        return providers.get(id).charge(charge);
    }
}