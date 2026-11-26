package com.playtomic.tests.wallet.observability;

import com.playtomic.tests.common.configs.CommonProps;
import com.playtomic.tests.common.observability.ReactiveOperationMetrics;
import com.playtomic.tests.wallet.enums.WalletOperationType;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Locale;

import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP;

/** Instruments wallet mutations and provider charges separately. */
@Component
public class WalletMutationMetrics {
    private static final String TOP_UP_OPERATION = "wallet.topup";
    private static final String SPEND_OPERATION = "wallet.spend";
    private static final String CHARGE_OPERATION = "wallet.payment.charge";
    private static final String OPERATION_RESULTS_COUNTER = "wallet.operation.results";
    private static final String OPERATION_TAG = "operation";
    private static final String STATUS_TAG = "status";

    private final MeterRegistry registry;
    private final ReactiveOperationMetrics topUps;
    private final ReactiveOperationMetrics spending;
    private final ReactiveOperationMetrics charges;

    WalletMutationMetrics(MeterRegistry registry, CommonProps common) {
        this.registry = registry;
        var settings = common.getObservability();
        this.topUps = new ReactiveOperationMetrics(registry, TOP_UP_OPERATION, settings);
        this.spending = new ReactiveOperationMetrics(registry, SPEND_OPERATION, settings);
        this.charges = new ReactiveOperationMetrics(registry, CHARGE_OPERATION, settings);
    }

    /** Measures a wallet mutation using its bounded operation-type label. */
    public Mono<WalletOperationModel> mutation(WalletOperationType type, Mono<WalletOperationModel> source) {
        String operationLabel = type.metricLabel();
        var timer = type == WALLET_OPERATION_TYPE_TOP_UP ? topUps : spending;
        return timer.measure(source.doOnNext(result -> registry.counter(OPERATION_RESULTS_COUNTER,
                OPERATION_TAG, operationLabel, STATUS_TAG, result.getStatus().getName().toUpperCase(Locale.ROOT)).increment()));
    }

    /** Measures the provider charge separately from the enclosing wallet mutation. */
    public Mono<PaymentReceiptModel> charge(Mono<PaymentReceiptModel> source) {
        return charges.measure(source);
    }
}