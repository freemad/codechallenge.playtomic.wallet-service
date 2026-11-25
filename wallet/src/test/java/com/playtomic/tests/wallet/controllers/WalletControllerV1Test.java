package com.playtomic.tests.wallet.controllers;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.exceptions.CommonExceptionHandler;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.mappers.WalletResponseMapperV1;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.services.WalletLookupService;
import com.playtomic.tests.wallet.services.WalletService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.MethodNotAllowedException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalletControllerV1Test {
    private final WalletLookupService lookup = mock(WalletLookupService.class);
    private final WalletResponseMapperV1 mapper = Mappers.getMapper(WalletResponseMapperV1.class);
    private final WebTestClient client = WebTestClient.bindToController(new WalletControllerV1(lookup, mapper))
            .controllerAdvice(new CommonExceptionHandler())
            .build();

    @Test
    void returnsTheWalletInAVersionedEnvelope() {
        when(lookup.findById("wallet-1"))
                .thenReturn(Mono.just(Outcome.success(new WalletModel("wallet-1", new BigDecimal("12.50"), "EUR"))));

        client.get().uri("/wallet-1").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.type").isEqualTo("response")
                .jsonPath("$.version").isEqualTo("1")
                .jsonPath("$.cid").isNotEmpty()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.result.id").isEqualTo("wallet-1")
                .jsonPath("$.result.balance").isEqualTo(12.5)
                .jsonPath("$.result.currency").isEqualTo("EUR")
                .jsonPath("$.result.type").doesNotHaveJsonPath()
                .jsonPath("$.result.version").doesNotHaveJsonPath();
    }

    @Test
    void returnsATimestampInTheLogEnvelope() {
        client.get().uri("/log").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.result.string").value((String timestamp) -> assertThat(timestamp).matches("[0-9]+"));
        verifyNoInteractions(lookup);
    }

    @Test
    void doesNotServeTheLegacyRoute() {
        client.get().uri("/wallets/wallet-1").exchange()
                .expectStatus().isNotFound();
        verifyNoInteractions(lookup);
    }

    @Test
    void returnsBadRequestForABlankId() {
        var clientWithValidation = WebTestClient.bindToController(
                new WalletControllerV1(new WalletLookupService(mock(WalletService.class)), mapper)).build();

        clientWithValidation.get().uri("/{id}", " ").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.INVALID_WALLET_ID.getCode());
    }

    @Test
    void returnsNotFoundWithAnErrorEnvelope() {
        when(lookup.findById("missing")).thenReturn(Mono.just(Outcome.failure(
                new BusinessException(WalletErrorCode.WALLET_NOT_FOUND).toError())));

        client.get().uri("/missing").exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.WALLET_NOT_FOUND.getCode());
    }

    @Test
    void hidesInternalFailureDetails() {
        var error = new BusinessException(WalletErrorCode.BALANCE_UPDATE_NOT_MATCHED).toError();
        error.setMessage("private-storage-detail");
        when(lookup.findById("broken")).thenReturn(Mono.just(Outcome.failure(error)));

        client.get().uri("/broken").exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.message").isEqualTo("Internal Server Error");
    }

    @Test
    void wrapsUnexpectedReactiveControllerFailuresWithoutExposingDetails() {
        when(lookup.findById("broken")).thenReturn(Mono.error(new RuntimeException("private-storage-detail")));

        client.get().uri("/broken").exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("response")
                .jsonPath("$.version").isEqualTo("1")
                .jsonPath("$.cid").isNotEmpty()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.result").hasJsonPath()
                .jsonPath("$.result").doesNotExist()
                .jsonPath("$.error.code").isEqualTo(CommonErrorCode.INTERNAL_SERVICE_EXCEPTION.getCode())
                .jsonPath("$.error.message").isEqualTo("Internal Server Error");
    }

    @Test
    void preservesTheBusinessCodeOfASynchronousControllerFailure() {
        when(lookup.findById("broken")).thenThrow(new BusinessException(
                WalletErrorCode.BALANCE_UPDATE_NOT_MATCHED, "private-storage-detail"));

        client.get().uri("/broken").exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.BALANCE_UPDATE_NOT_MATCHED.getCode())
                .jsonPath("$.error.message").isEqualTo("Internal Server Error");
    }

    @Test
    void preservesTheHttpStatusAndHeadersOfAControllerHttpException() {
        when(lookup.findById("broken")).thenReturn(Mono.error(
                new MethodNotAllowedException(HttpMethod.GET, Set.of(HttpMethod.POST))));

        client.get().uri("/broken").exchange()
                .expectStatus().isEqualTo(405)
                .expectHeader().valueEquals("Allow", "POST")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(CommonErrorCode.PROTOCOL_EXCEPTION.getCode())
                .jsonPath("$.error.message").isEqualTo("Method Not Allowed");
    }
}