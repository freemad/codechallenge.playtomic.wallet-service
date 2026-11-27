package com.playtomic.tests.wallet.services;

import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import com.playtomic.tests.wallet.services.WalletTopUpProviders.TopUpChargeStrategy;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.*;

class WalletTopUpProvidersTest {
    @Test
    void dispatchesOnlyToTheRequestedStrategy() {
        var first = mock(TopUpChargeStrategy.class);
        var second = mock(TopUpChargeStrategy.class);
        var providers = new WalletTopUpProviders(Map.of("first", first, "second", second));
        var request = new CardChargeModel("fixture-card", BigDecimal.ONE);
        var receipt = new PaymentReceiptModel("second-receipt");
        when(second.charge(request)).thenReturn(Mono.just(receipt));

        StepVerifier.create(providers.charge("second", request)).expectNext(receipt).verifyComplete();

        verify(second).charge(request);
        verifyNoMoreInteractions(second);
        verifyNoInteractions(first);
    }
}

