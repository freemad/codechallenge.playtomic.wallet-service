package com.playtomic.tests.wallet.http.stripe;

import com.playtomic.tests.common.utils.ExceptionNormalizer;
import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.PaymentRefundModel;
import com.playtomic.tests.wallet.services.adapter.StripeAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/** Compatibility facade for the supplied Stripe service API. */
@Service
@RequiredArgsConstructor
public class StripeService {
    private final StripeAdapter adapter;

    public Mono<Payment> charge(String creditCardNumber, BigDecimal amount) {
        return Mono.defer(() -> adapter.charge(new CardChargeModel(creditCardNumber, amount)))
                .map(receipt -> new Payment(receipt.getId()))
                .onErrorMap(ExceptionNormalizer::normalize);
    }

    public Mono<Void> refund(String paymentId) {
        return Mono.defer(() -> adapter.refund(new PaymentRefundModel(paymentId)))
                .onErrorMap(ExceptionNormalizer::normalize);
    }
}