package com.playtomic.tests.wallet.observability;

import com.playtomic.tests.common.configs.CommonProps;
import com.playtomic.tests.common.observability.ReactiveOperationMetrics;
import com.playtomic.tests.wallet.http.stripe.Payment;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class WalletMutationMetrics {
    private final MeterRegistry registry;
    private final ReactiveOperationMetrics topUps;
    private final ReactiveOperationMetrics spending;
    private final ReactiveOperationMetrics charges;

    WalletMutationMetrics(MeterRegistry registry, CommonProps common) {
        this.registry = registry;
        var settings = common.getObservability();
        this.topUps = new ReactiveOperationMetrics(registry, "wallet.topup", settings);
        this.spending = new ReactiveOperationMetrics(registry, "wallet.spend", settings);
        this.charges = new ReactiveOperationMetrics(registry, "wallet.payment.charge", settings);
    }

    public Mono<WalletOperationModel> mutation(WalletOperationModel.Type type, Mono<WalletOperationModel> source) {
        var timer = type == WalletOperationModel.Type.TOP_UP ? topUps : spending;
        return timer.measure(source.doOnNext(result -> registry.counter("wallet.operation.results",
                "operation", type.name(), "status", result.getStatus().name()).increment()));
    }

    public Mono<Payment> charge(Mono<Payment> source) {
        return charges.measure(source);
    }
}