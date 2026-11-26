package com.playtomic.tests.wallet.services.adapter;

import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.mappers.StripePaymentMapper;
import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import com.playtomic.tests.wallet.models.PaymentRefundModel;
import com.playtomic.tests.wallet.services.client.StripeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static java.util.Objects.requireNonNull;

/** Translates wallet payment models into Stripe client calls and maps charge receipts back. */
@Service
@RequiredArgsConstructor
public final class StripeAdapter {
    private final StripeClient client;
    private final StripePaymentMapper mapper;

    /** Converts the charge model to the Stripe request and maps an accepted receipt back to the wallet. */
    public Mono<PaymentReceiptModel> charge(CardChargeModel model) {
        return Mono.defer(() -> client.charge(mapper.toDto(requireNonNull(model, "Charge is required"))))
                .map(mapper::toModel)
                .onErrorMap(ExceptionNormalizer::normalize);
    }

    /** Requests a refund for a provider payment, not a return of a wallet purchase. */
    public Mono<Void> refund(PaymentRefundModel model) {
        return Mono.defer(() -> client.refund(mapper.toDto(requireNonNull(model, "Refund is required"))))
                .onErrorMap(ExceptionNormalizer::normalize);
    }
}

