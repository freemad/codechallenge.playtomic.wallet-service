package com.playtomic.tests.wallet;

import com.playtomic.tests.common.exceptions.CommonExceptionHandler;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.wallet.configs.WalletReconciliationProps;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import com.playtomic.tests.wallet.enums.WalletOperationMongoType;
import com.playtomic.tests.wallet.http.stripe.StripeServiceException;
import com.playtomic.tests.wallet.jobs.WalletReconciliationJob;
import com.playtomic.tests.wallet.models.CardChargeModel;
import com.playtomic.tests.wallet.models.PaymentReceiptModel;
import com.playtomic.tests.wallet.models.WalletOperationModel;
import com.playtomic.tests.wallet.repositories.WalletOperationRepository;
import com.playtomic.tests.wallet.services.WalletMutationService;
import com.playtomic.tests.wallet.services.WalletLookupService;
import com.playtomic.tests.wallet.services.WalletOperationService;
import com.playtomic.tests.wallet.services.adapter.StripeAdapter;
import com.playtomic.tests.wallet.services.client.StripeClient;
import com.playtomic.tests.wallet.services.entity.WalletJobLeaseEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOperationEntityService;
import com.playtomic.tests.wallet.services.entity.WalletOutboxEntityService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.mongodb.MongoDBContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static com.playtomic.tests.common.constants.HttpParam.HEADER_IDEMPOTENCY_KEY;
import static com.playtomic.tests.wallet.enums.WalletOperationStatus.*;
import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT15S")
@ActiveProfiles({"test", "local-top-up"})
@Import(WalletApplicationIT.MongoConfiguration.class)
class WalletApplicationIT {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private ReactiveMongoTemplate mongo;

    @Autowired
    private WalletMutationService mutations;

    @Autowired
    private WalletLookupService wallets;

    @Autowired
    private WalletOperationService operations;

    @Autowired
    private WalletOperationEntityService operationEntities;

    @Autowired
    private WalletJobLeaseEntityService leases;

    @Autowired
    private WalletReconciliationProps reconciliationProperties;

    @Autowired
    private WalletOperationRepository operationRepository;

    @Autowired
    private WalletOutboxEntityService outbox;

    @MockitoBean
    private StripeAdapter stripe;

    private static final Duration DEADLINE = Duration.ofSeconds(15);
    private static final String TEST_CARD = "4111111111111111";
    private static final String HTTP_TEST_CARD = "4242424242424242";
    private static final String WALLETS_PATH = "/api/v1/wallets";
    private static final String LOOKUP_PATH = WALLETS_PATH + "/{id}";
    private static final String TOP_UP_PATH = LOOKUP_PATH + "/top-ups";

    @Test
    void listsOnlyActiveWalletTopUpProviders() {
        webClient.get().uri(WALLETS_PATH + "/payment-providers").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store").expectBody()
                .jsonPath("$.result.providers.length()").isEqualTo(1)
                .jsonPath("$.result.providers[0]").isEqualTo("stripe");
        verifyNoInteractions(stripe);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void servesTopUpOverHttpAndReplaysWithoutChargingTwice(CapturedOutput output) {
        String id = "http-credit";
        var charge = new CardChargeModel(HTTP_TEST_CARD, new BigDecimal("5.25"));
        when(stripe.charge(charge)).thenReturn(Mono.just(new PaymentReceiptModel("payment-http-credit")));
        StepVerifier.create(seedWallet(id, "10.00")).expectComplete().verify(DEADLINE);

        var first = postTopUp(id, "same-key", "5.25").expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store").expectBody()
                .jsonPath("$.type").isEqualTo("response")
                .jsonPath("$.version").isEqualTo("1")
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.result.status").isEqualTo("succeeded")
                .jsonPath("$.result.walletId").isEqualTo(id)
                .jsonPath("$.result.currency").isEqualTo("EUR")
                .jsonPath("$.result.provider").isEqualTo("stripe")
                .jsonPath("$.result.amount").isEqualTo(5.25)
                .jsonPath("$.result.resultingBalance").isEqualTo(15.25)
                .jsonPath("$.result.creditCardNumber").doesNotHaveJsonPath()
                .jsonPath("$.result.version").doesNotHaveJsonPath()
                .returnResult();
        String operationId = JSON.readTree(first.getResponseBody()).path("result").path("operationId").asString();
        assertThat(operationId).isNotBlank();
        StepVerifier.create(mongo.findById(operationId, Document.class, "wallet_operations"))
                .assertNext(row -> assertThat(row.getString("provider")).isEqualTo("stripe"))
                .expectComplete().verify(DEADLINE);
        StepVerifier.create(mongo.updateFirst(Query.query(Criteria.where("_id").is(operationId)),
                        new Update().unset("provider"), "wallet_operations"))
                .assertNext(result -> assertThat(result.getModifiedCount()).isEqualTo(1))
                .expectComplete().verify(DEADLINE);
        postTopUp(id, "same-key", "5.250").expectStatus().isOk().expectBody()
                .jsonPath("$.result.operationId").isEqualTo(operationId)
                .jsonPath("$.result.provider").isEqualTo("stripe")
                .jsonPath("$.result.resultingBalance").isEqualTo(15.25);
        webClient.post().uri(TOP_UP_PATH, id).contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_IDEMPOTENCY_KEY, "same-key").bodyValue(topUpBody("5.25", HTTP_TEST_CARD).replace("stripe", "paypal"))
                .exchange().expectStatus().isEqualTo(409).expectBody()
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.IDEMPOTENCY_CONFLICT.getCode());
        webClient.get().uri(LOOKUP_PATH, id).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.result.balance").isEqualTo(15.25);
        verify(stripe, times(1)).charge(charge);
        var records = output.getOut().lines().filter(line -> line.startsWith("{")).map(JSON::readTree)
                .filter(node -> "wallet.events".equals(node.path("logger_name").asString(""))).toList();
        assertThat(records.toString()).doesNotContain(HTTP_TEST_CARD, "same-key", id, "payment-http-credit");
        assertThat(records.stream().filter(node -> operationId.equals(node.path("operationId").asString("")))
                .map(node -> node.path("event").asString()).toList()).containsExactly(
                "operation.reserved", "payment.dispatch", "payment.receipt_returned", "receipt.committed", "operation.finalized");
    }

    @Test
    void returnsAcceptedOnUncertainChargeAndDoesNotRedispatchOnHttpReplay() {
        String id = "http-uncertain";
        var charge = new CardChargeModel(HTTP_TEST_CARD, BigDecimal.ONE);
        when(stripe.charge(charge)).thenReturn(Mono.error(new StripeServiceException("timeout")));
        StepVerifier.create(seedWallet(id, "0")).expectComplete().verify(DEADLINE);
        for (int attempt = 0; attempt < 2; attempt++) {
            postTopUp(id, "key", "1").expectStatus().isAccepted().expectBody()
                    .jsonPath("$.result.status").isEqualTo("review_required")
                    .jsonPath("$.result.resultingBalance").doesNotHaveJsonPath();
        }
        verify(stripe, times(1)).charge(charge);
    }

    @Test
    void rejectsInvalidHttpTopUpsBeforeDispatch() {
        String id = "http-invalid";
        StepVerifier.create(seedWallet(id, "0")).expectComplete().verify(DEADLINE);
        webClient.post().uri(TOP_UP_PATH, id).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(topUpBody("1", HTTP_TEST_CARD)).exchange().expectStatus().isBadRequest();
        // A whitespace-only field value is rejected by the Netty client before dispatch.
        postTopUp(id, "", "1").expectStatus().isBadRequest();
        webClient.post().uri(TOP_UP_PATH, id).contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_IDEMPOTENCY_KEY, "first", "second")
                .bodyValue(topUpBody("1", HTTP_TEST_CARD)).exchange().expectStatus().isBadRequest();
        webClient.post().uri(TOP_UP_PATH, id).contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_IDEMPOTENCY_KEY, "key").bodyValue("{").exchange().expectStatus().isBadRequest();
        webClient.post().uri(TOP_UP_PATH, id).contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_IDEMPOTENCY_KEY, "key").bodyValue(topUpBody("1", HTTP_TEST_CARD).replace("EUR", "USD"))
                .exchange().expectStatus().isBadRequest().expectBody()
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.CURRENCY_MISMATCH.getCode());
        webClient.post().uri(TOP_UP_PATH, id).contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_IDEMPOTENCY_KEY, "key").bodyValue(topUpBody("1", TEST_CARD)).exchange()
                .expectStatus().isBadRequest().expectBody()
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.INVALID_REQUEST.getCode());
        for (String body : java.util.List.of(topUpBody("1", HTTP_TEST_CARD).replace("\"provider\":\"stripe\",", ""),
                topUpBody("1", HTTP_TEST_CARD).replace("stripe", ""),
                topUpBody("1", HTTP_TEST_CARD).replace("stripe", "paypal"))) {
            webClient.post().uri(TOP_UP_PATH, id).contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_IDEMPOTENCY_KEY, "key").bodyValue(body).exchange()
                    .expectStatus().isBadRequest().expectBody()
                    .jsonPath("$.error.code").isEqualTo(WalletErrorCode.INVALID_REQUEST.getCode());
        }
        postTopUp(id, "key", "1.1234567890123456789012345678901234")
                .expectStatus().isBadRequest().expectBody()
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.INVALID_REQUEST.getCode());
        postTopUp("http-missing", "key", "1").expectStatus().isNotFound().expectBody()
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.WALLET_NOT_FOUND.getCode());
        verifyNoInteractions(stripe);
    }

    @Test
    void servesHttpAndConnectsToAReactiveMongoReplicaSet() {
        assertThat(applicationContext.getBeansOfType(CommonExceptionHandler.class)).hasSize(1);

        webClient.get().uri(WALLETS_PATH + "/log").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result.string").isNotEmpty();

        StepVerifier.create(mongo.executeCommand("{\"hello\":1}"))
                .assertNext(result -> {
                    assertThat(result.getString("setName")).isNotBlank();
                    assertThat(result.getBoolean("isWritablePrimary")).isTrue();
                })
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void looksUpAStringIdAndReturnsTheExactStoredBalance() {
        String id = "0123456789abcdef01234567";
        BigDecimal balance = new BigDecimal("9007199254740993.01");
        var document = new Document("_id", id)
                .append("balance", new Decimal128(balance))
                .append("currency", "EUR")
                .append("_class", "com.playtomic.tests.wallet.repository.WalletEntity");
        StepVerifier.create(mongo.execute("wallets", collection -> collection.insertOne(document)).then())
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        webClient.get().uri(LOOKUP_PATH, id).exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    var body = JSON.readTree(result.getResponseBody());
                    assertThat(body.path("type").asString()).isEqualTo("response");
                    assertThat(body.path("version").asString()).isEqualTo("1");
                    assertThat(body.path("cid").asString()).isNotBlank();
                    assertThat(body.path("success").asBoolean()).isTrue();
                    var wallet = body.path("result");
                    assertThat(wallet.path("id").asString()).isEqualTo(id);
                    assertThat(wallet.path("balance").isNumber()).isTrue();
                    assertThat(wallet.path("balance").decimalValue()).isEqualByComparingTo(balance);
                    assertThat(wallet.path("currency").asString()).isEqualTo("EUR");
                });
    }

    @Test
    void returnsNotFoundForAnUnknownWallet() {
        webClient.get().uri(LOOKUP_PATH, "missing").exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.WALLET_NOT_FOUND.getCode());
    }

    @Test
    void rejectsABlankWalletId() {
        webClient.get().uri(LOOKUP_PATH, " ").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(WalletErrorCode.INVALID_WALLET_ID.getCode());
    }

    @Test
    void returnsInternalServerErrorWithoutExposingStoredDataWhenWalletCannotBeMapped() {
        String id = "corrupt-wallet";
        String invalidBalance = "private-storage-detail";
        StepVerifier.create(mongo.insert(new Document("_id", id).append("currency", "EUR")
                        .append("balance", invalidBalance), "wallets"))
                .expectNextCount(1).expectComplete().verify(DEADLINE);

        webClient.get().uri(LOOKUP_PATH, id).exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.message").isEqualTo("Internal Server Error")
                .consumeWith(result -> assertThat(new String(result.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8))
                        .doesNotContain(invalidBalance));
    }

    @Test
    void concurrentSameKeyTopUpsChargeOnceAndReplayTheOriginalBalance() {
        BigDecimal amount = new BigDecimal("5.25");
        when(stripe.charge(new CardChargeModel(TEST_CARD, amount))).thenReturn(Mono.delay(Duration.ofMillis(150))
                .thenReturn(new PaymentReceiptModel("payment-concurrent")));
        Mono<WalletOperationModel> topUp = mutations.topUp("credit-race", "same-key", amount, "EUR", TEST_CARD)
                .map(WalletApplicationIT::successfulResult);
        StepVerifier.create(seedWallet("credit-race", "10.00")
                        .thenMany(Flux.range(0, 8).flatMap(ignored -> topUp))
                        .collectList()
                        .doOnNext(results -> {
                            assertThat(results).hasSize(8).allSatisfy(result -> {
                                assertThat(result.getStatus()).isIn(WALLET_OPERATION_STATUS_PENDING, WALLET_OPERATION_STATUS_SUCCEEDED);
                                assertThat(result.getId()).isEqualTo("7a6dc2d73b0ad630b1681894d4c5bb2eb301d0dfbc01dca506d140f31ae77d82");
                            });
                            assertThat(results).anyMatch(result -> result.getStatus() == WALLET_OPERATION_STATUS_SUCCEEDED);
                        })
                        .then(checkCommittedBalance("credit-race", "15.25", "5.25", 1))
                        .then(mutations.spend("credit-race", "new-key", BigDecimal.ONE, "EUR")
                                .doOnNext(spend -> assertThat(successfulResult(spend).getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED)))
                        .then(topUp))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED);
                    assertThat(result.getResultingBalance()).isEqualByComparingTo("15.25");
                })
                .expectComplete().verify(DEADLINE);
        verify(stripe, times(1)).charge(new CardChargeModel(TEST_CARD, amount));
        StepVerifier.create(outboxCount("credit-race", 1))
                .expectNext(1L).expectComplete().verify(DEADLINE);
        StepVerifier.create(mutations.spend("credit-race", "same-key", amount, "EUR"))
                .assertNext(result -> assertFailure(result, WalletErrorCode.IDEMPOTENCY_CONFLICT))
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void concurrentDistinctTopUpsBothCreditTheWallet() {
        String id = "two-credits";
        var first = new BigDecimal("5.25");
        var second = new BigDecimal("2.00");
        when(stripe.charge(new CardChargeModel(TEST_CARD, first))).thenReturn(Mono.just(new PaymentReceiptModel("two-credits-a")));
        when(stripe.charge(new CardChargeModel(TEST_CARD, second))).thenReturn(Mono.just(new PaymentReceiptModel("two-credits-b")));
        StepVerifier.create(seedWallet(id, "10.00").thenMany(Flux.merge(
                        mutations.topUp(id, "a", first, "EUR", TEST_CARD), mutations.topUp(id, "b", second, "EUR", TEST_CARD)))
                        .map(WalletApplicationIT::successfulResult).collectList())
                .assertNext(results -> {
                    assertThat(results).hasSize(2).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED));
                    assertThat(results).extracting(WalletOperationModel::getId).doesNotHaveDuplicates();
                }).expectComplete().verify(DEADLINE);
        StepVerifier.create(checkCommittedBalance(id, "17.25", "7.25", 2).then(outboxCount(id, 1)))
                .expectNext(2L).expectComplete().verify(DEADLINE);
        verify(stripe).charge(new CardChargeModel(TEST_CARD, first));
        verify(stripe).charge(new CardChargeModel(TEST_CARD, second));
        verifyNoMoreInteractions(stripe);
    }

    @Test
    void concurrentSameKeySpendsDebitOnlyOnceEvenWhenBothAreAffordable() {
        String id = "duplicate-debit";
        var spend = mutations.spend(id, "same", new BigDecimal("7.00"), "EUR").map(WalletApplicationIT::successfulResult);
        StepVerifier.create(seedWallet(id, "20.00").thenMany(Flux.merge(spend, spend)).collectList())
                .assertNext(results -> {
                    assertThat(results).hasSize(2).allSatisfy(r -> {
                        assertThat(r.getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED);
                        assertThat(r.getResultingBalance()).isEqualByComparingTo("13.00");
                    });
                    assertThat(results.getFirst().getId()).isEqualTo(results.getLast().getId());
                }).expectComplete().verify(DEADLINE);
        StepVerifier.create(checkCommittedBalance(id, "13.00", "-7.00", 1).then(outboxCount(id, 2)))
                .expectNext(1L).expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @Test
    @EnabledIfSystemProperty(named = "wallet.pressure", matches = "true")
    void pressureConcurrentSpendsOnOneWallet() {
        String id = "pressure-debit";
        StepVerifier.create(seedWallet(id, "20").thenMany(Flux.range(0, 40).flatMap(i ->
                        mutations.spend(id, "spend-" + i, BigDecimal.ONE, "EUR"), 4))
                        .map(WalletApplicationIT::successfulResult).collectList())
                .assertNext(results -> {
                    assertThat(results).hasSize(40).extracting(WalletOperationModel::getId).doesNotHaveDuplicates();
                    assertThat(results).filteredOn(r -> r.getStatus() == WALLET_OPERATION_STATUS_SUCCEEDED).hasSize(20);
                    assertThat(results).filteredOn(r -> r.getStatus() == WALLET_OPERATION_STATUS_INSUFFICIENT_FUNDS).hasSize(20);
                }).expectComplete().verify(Duration.ofSeconds(60));
        StepVerifier.create(checkCommittedBalance(id, "0", "-20", 20).then(outboxCount(id, 2)))
                .expectNext(20L).expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @Test
    void inexactCreditNeverDispatches() {
        String id = "credit-overflow";
        StepVerifier.create(seedWallet(id, "1E+34").then(mutations.topUp(id, "key", BigDecimal.ONE, "EUR", TEST_CARD)))
                .assertNext(result -> assertFailure(result, WalletErrorCode.INVALID_REQUEST)).verifyComplete();
        verifyNoInteractions(stripe);
    }

    @Test
    void concurrentCreditsCannotBothClaimLastExactUnit() {
        String id = "last-exact-unit";
        var call = new CardChargeModel(TEST_CARD, BigDecimal.ONE);
        var dispatched = reactor.core.publisher.Sinks.<Void>empty();
        var release = reactor.core.publisher.Sinks.<Void>empty();
        when(stripe.charge(call)).thenAnswer(inv -> {
            dispatched.tryEmitEmpty();
            return release.asMono().thenReturn(new PaymentReceiptModel(id));
        });
        var first = mutations.topUp(id, "a", BigDecimal.ONE, "EUR", TEST_CARD);
        var second = dispatched.asMono().then(mutations.topUp(id, "b", BigDecimal.ONE, "EUR", TEST_CARD))
                .doOnNext(result -> assertFailure(result, WalletErrorCode.INVALID_REQUEST))
                .doFinally(signal -> release.tryEmitEmpty());
        StepVerifier.create(seedWallet(id, "9".repeat(34)).then(Mono.when(first.doOnNext(result ->
                        assertThat(successfulResult(result).getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED)), second))
                        .then(checkCommittedBalance(id, "1E+34", "1", 1)))
                .expectComplete().verify(DEADLINE);
        verify(stripe).charge(call);
    }

    @Test
    @EnabledIfSystemProperty(named = "wallet.pressure", matches = "true")
    void pressureConcurrentCreditsOnOneWallet() {
        String id = "pressure-credit";
        var call = new CardChargeModel(TEST_CARD, BigDecimal.ONE);
        when(stripe.charge(call)).thenAnswer(inv -> Mono.just(new PaymentReceiptModel(UUID.randomUUID().toString())));
        StepVerifier.create(seedWallet(id, "0").thenMany(Flux.range(0, 40).flatMap(i ->
                        mutations.topUp(id, "credit-" + i, BigDecimal.ONE, "EUR", TEST_CARD), 4))
                        .map(WalletApplicationIT::successfulResult).collectList())
                .assertNext(results -> {
                    assertThat(results).hasSize(40).extracting(WalletOperationModel::getId).doesNotHaveDuplicates();
                    assertThat(results).allMatch(r -> r.getStatus() == WALLET_OPERATION_STATUS_SUCCEEDED);
                }).expectComplete().verify(Duration.ofSeconds(60));
        verify(stripe, times(40)).charge(call);
        StepVerifier.create(checkCommittedBalance(id, "40", "40", 40).then(outboxCount(id, 1))).expectNext(40L).verifyComplete();
    }

    @Test
    void recordedDeclineIsNotACreditEvenOnReplay() {
        String id = "http-declined";
        var charge = new CardChargeModel(HTTP_TEST_CARD, BigDecimal.ONE);
        when(stripe.charge(charge)).thenReturn(Mono.error(new com.playtomic.tests.wallet.http.stripe.StripeAmountTooSmallException()));
        StepVerifier.create(seedWallet(id, "10")).verifyComplete();
        for (int attempt = 0; attempt < 2; attempt++) {
            postTopUp(id, "key", "1").expectStatus().isOk().expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.result.status").isEqualTo("payment_rejected")
                    .jsonPath("$.result.resultingBalance").doesNotHaveJsonPath();
        }
        webClient.get().uri(LOOKUP_PATH, id).exchange().expectStatus().isOk().expectBody().jsonPath("$.result.balance").isEqualTo(10);
        verify(stripe).charge(charge);
    }

    @Test
    void invalidMissingAndWrongCurrencyRequestsFailWithoutDispatch() {
        StepVerifier.create(seedWallet("currency-check", "10").thenMany(Flux.concat(
                        mutations.topUp("currency-check", "invalid", BigDecimal.ZERO, "EUR", TEST_CARD),
                        mutations.topUp("missing-for-mutation", "valid", BigDecimal.ONE, "EUR", TEST_CARD),
                        mutations.topUp("currency-check", "wrong-currency", BigDecimal.ONE, "USD", TEST_CARD))))
                .assertNext(result -> assertFailure(result, WalletErrorCode.INVALID_REQUEST))
                .assertNext(result -> assertFailure(result, WalletErrorCode.WALLET_NOT_FOUND))
                .assertNext(result -> assertFailure(result, WalletErrorCode.CURRENCY_MISMATCH))
                .expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @Test
    void concurrentDebitsCannotOverdrawAndRejectedDebitIsReplayed() {
        BigDecimal amount = new BigDecimal("7.00");
        StepVerifier.create(seedWallet("debit-race", "10.00")
                        .thenMany(Flux.just("first", "second").flatMap(key ->
                                mutations.spend("debit-race", key, amount, "EUR")
                                        .map(WalletApplicationIT::successfulResult)
                                        .flatMap(result -> mutations.spend("debit-race", key, amount, "EUR")
                                                .map(WalletApplicationIT::successfulResult)
                                                .doOnNext(replay -> assertThat(replay).isEqualTo(result)))))
                .map(WalletOperationModel::getStatus).collectList())
                .assertNext(statuses -> assertThat(statuses).containsExactlyInAnyOrder(
                        WALLET_OPERATION_STATUS_SUCCEEDED, WALLET_OPERATION_STATUS_INSUFFICIENT_FUNDS))
                .expectComplete().verify(DEADLINE);
        StepVerifier.create(wallets.findById("debit-race").map(result -> successfulResult(result).getBalance()))
                .assertNext(balance -> assertThat(balance).isEqualByComparingTo("3.00"))
                .expectComplete().verify(DEADLINE);
        StepVerifier.create(outboxCount("debit-race", 2))
                .expectNext(1L).expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @Test
    void uncertainChargeIsNotDispatchedOnReplay() {
        var charge = new CardChargeModel(TEST_CARD, BigDecimal.ONE);
        var providerJournal = new java.util.concurrent.atomic.AtomicReference<String>();
        when(stripe.charge(charge)).thenReturn(Mono.defer(() -> {
            providerJournal.set("confirmed-uncertain-charge"); // Provider accepted before its response was lost.
            return Mono.error(new StripeServiceException("Timeout"));
        }));
        Mono<WalletOperationModel> topUp = mutations.topUp("uncertain", "key", BigDecimal.ONE, "EUR", TEST_CARD)
                .map(WalletApplicationIT::successfulResult);
        StepVerifier.create(seedWallet("uncertain", "0").thenMany(Flux.concat(topUp, topUp))
                        .map(WalletOperationModel::getStatus))
                .expectNext(WALLET_OPERATION_STATUS_REVIEW_REQUIRED, WALLET_OPERATION_STATUS_REVIEW_REQUIRED)
                .expectComplete().verify(DEADLINE);
        StepVerifier.create(topUp.flatMap(review -> operations.recordReceipt(review.getId(), providerJournal.get()))
                        .then(topUp).then(topUp))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED))
                .expectComplete().verify(DEADLINE);
        StepVerifier.create(checkCommittedBalance("uncertain", "1", "1", 1).then(outboxCount("uncertain", 1)))
                .expectNext(1L).verifyComplete();
        verify(stripe, times(1)).charge(charge);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void recordedReceiptSurvivesAFailedOutboxWriteAndCanBeAppliedLater(CapturedOutput output) {
        String id = "receipt-recovery-operation";
        var request = new WalletOperationModel(id, "receipt-recovery", WALLET_OPERATION_TYPE_TOP_UP,
                new BigDecimal("2.00"), "EUR", WALLET_OPERATION_STATUS_PENDING, null, Instant.now(), "stripe");
        Query eventId = Query.query(Criteria.where("_id").is(id));
        StepVerifier.create(seedWallet("receipt-recovery", "4.00").then(operations.reserve(request))
                        .doOnNext(WalletApplicationIT::presentResult)
                        .then(operations.recordReceipt(id, "payment-recovery"))
                        .doOnNext(result -> assertThat(presentResult(result).getStatus()).isEqualTo(WALLET_OPERATION_STATUS_RECEIPT_RECORDED))
                        .then(mongo.insert(new Document("_id", id), "wallet_outbox"))
                        .then(operations.apply(id)))
                .expectError(org.springframework.dao.DuplicateKeyException.class).verify(DEADLINE);
        assertThat(output.getOut()).contains("\"event\":\"receipt.committed\"").doesNotContain("\"event\":\"operation.finalized\"");
        Query postings = Query.query(Criteria.where("walletId").is("receipt-recovery"));
        StepVerifier.create(mongo.count(postings, "wallet_ledger")).expectNext(0L).verifyComplete();
        StepVerifier.create(operations.find(id).map(WalletApplicationIT::presentResult)
                .zipWith(wallets.findById("receipt-recovery").map(WalletApplicationIT::successfulResult)))
                .assertNext(pair -> {
                    assertThat(pair.getT1().getStatus()).isEqualTo(WALLET_OPERATION_STATUS_RECEIPT_RECORDED);
                    assertThat(pair.getT2().getBalance()).isEqualByComparingTo("4.00");
                }).expectComplete().verify(DEADLINE);
        StepVerifier.create(mongo.remove(eventId, "wallet_outbox").then(operations.apply(id))
                        .map(WalletApplicationIT::presentResult))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED);
                    assertThat(result.getResultingBalance()).isEqualByComparingTo("6.00");
                }).expectComplete().verify(DEADLINE);
        StepVerifier.create(mongo.find(postings, Document.class, "wallet_ledger").single())
                .assertNext(row -> {
                    assertThat(row.getLong("sequence")).isEqualTo(1L);
                    assertThat(row.get("delta", Decimal128.class).bigDecimalValue()).isEqualByComparingTo("2.00");
                }).expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @Test
    void reconciliationAppliesAReceiptAndFlagsAStaleChargeWithoutContactingStripe() {
        Instant stale = Instant.now().minus(Duration.ofMinutes(5));
        String walletId = "reconciliation-wallet";
        var uncertain = new WalletOperationModel("stale-charge", walletId, WALLET_OPERATION_TYPE_TOP_UP,
                BigDecimal.ONE, "EUR", WALLET_OPERATION_STATUS_PENDING, null, stale, "stripe");
        var receipted = new WalletOperationModel("stale-receipt", walletId, WALLET_OPERATION_TYPE_TOP_UP,
                BigDecimal.ONE, "EUR", WALLET_OPERATION_STATUS_PENDING, null, stale, "stripe");
        var job = new WalletReconciliationJob(operations, operationEntities, leases, reconciliationProperties, mock(StripeClient.class));
        Query id = Query.query(Criteria.where("_id").is(receipted.getId()));
        Mono<Void> setup = seedWallet(walletId, "1").then(operations.reserve(uncertain))
                .doOnNext(WalletApplicationIT::presentResult)
                .then(operations.reserve(receipted)).doOnNext(WalletApplicationIT::presentResult)
                .then(operations.recordReceipt(receipted.getId(), "late-payment"))
                .doOnNext(WalletApplicationIT::presentResult)
                .then(mongo.updateFirst(id, new Update().set("updatedAt", Date.from(stale)), "wallet_operations"))
                .then();
        Mono<WalletOperationModel> outcome = operations.find(uncertain.getId()).map(WalletApplicationIT::presentResult);
        StepVerifier.create(setup.then(leases.acquire("payment-reconciliation", Duration.ofMinutes(1)))
                        .switchIfEmpty(Mono.error(() -> new AssertionError("Expected to acquire the lease")))
                        .doOnNext(token -> assertThat(UUID.fromString(token).version()).isEqualTo(7))
                        .flatMap(token -> job.reconcile().then(outcome)
                                .doOnNext(result -> assertThat(result.getStatus()).isEqualTo(WALLET_OPERATION_STATUS_PENDING))
                                .then(leases.release("payment-reconciliation", token)))
                        .then(job.reconcile())
                        .then(outcome.zipWith(operations.find(receipted.getId()).map(WalletApplicationIT::presentResult))))
                .assertNext(pair -> {
                    assertThat(pair.getT1().getStatus()).isEqualTo(WALLET_OPERATION_STATUS_REVIEW_REQUIRED);
                    assertThat(pair.getT2().getStatus()).isEqualTo(WALLET_OPERATION_STATUS_SUCCEEDED);
                    assertThat(pair.getT2().getResultingBalance()).isEqualByComparingTo("2");
                }).expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @Test
    void commitsReceiptWhileOldestPendingTransitionIsStalled() {
        String walletId = "isolated-receipt";
        Instant stale = Instant.EPOCH;
        var pending = new WalletOperationModel("blocked-pending", walletId, WALLET_OPERATION_TYPE_TOP_UP,
                BigDecimal.ONE, "EUR", WALLET_OPERATION_STATUS_PENDING, null, stale, "stripe");
        var receipt = new WalletOperationModel("independent-receipt", walletId, WALLET_OPERATION_TYPE_TOP_UP,
                BigDecimal.ONE, "EUR", WALLET_OPERATION_STATUS_PENDING, null, stale, "stripe");
        var started = reactor.core.publisher.Sinks.<Void>empty();
        var release = reactor.core.publisher.Sinks.<Void>empty();
        var commands = mock(WalletOperationService.class, org.mockito.AdditionalAnswers.delegatesTo(operations));
        doReturn(Mono.defer(() -> {
            started.tryEmitEmpty();
            return release.asMono().then(Mono.error(new IllegalStateException("pending transition failed")));
        })).when(commands).markPaymentOutcome(pending.getId(), WALLET_OPERATION_STATUS_REVIEW_REQUIRED);
        var job = new WalletReconciliationJob(commands, operationEntities, leases, reconciliationProperties, mock(StripeClient.class));
        var setup = seedWallet(walletId, "1").then(operations.reserve(pending)).then(operations.reserve(receipt))
                .then(operations.recordReceipt(receipt.getId(), "independent-payment"))
                .then(mongo.updateMulti(Query.query(Criteria.where("walletId").is(walletId)),
                        new Update().set("updatedAt", Date.from(stale)), "wallet_operations"));
        var confirm = started.asMono().thenMany(Flux.interval(Duration.ZERO, Duration.ofMillis(100)))
                .concatMap(tick -> operations.find(receipt.getId()).map(WalletApplicationIT::presentResult))
                .filter(row -> row.getStatus() == WALLET_OPERATION_STATUS_SUCCEEDED).next()
                .then(checkCommittedBalance(walletId, "2", "1", 1)).doOnSuccess(done -> release.tryEmitEmpty());
        StepVerifier.create(setup.then(Mono.when(job.reconcile(), confirm)))
                .expectComplete().verify(DEADLINE);
        verifyNoInteractions(stripe);
    }

    @Test
    void reconciliationQueryOrdersAndLimitsResultsAndDeferralFencesStaleObservations() {
        String prefix = "repository-reconciliation-";
        Instant cutoff = Instant.parse("2000-01-02T00:00:00Z");
        Instant oldest = cutoff.minusSeconds(1);
        Instant deferred = cutoff.plusSeconds(1);
        String first = prefix + "a";
        var ids = java.util.List.of(first, prefix + "b", prefix + "c", prefix + "late",
                prefix + "debit", prefix + "pending");
        var rows = java.util.List.of(
                operationRow(ids.get(1), 1, 3, oldest),
                operationRow(first, 1, 3, oldest),
                operationRow(ids.get(2), 1, 3, cutoff),
                operationRow(ids.get(3), 1, 3, deferred),
                operationRow(ids.get(4), 2, 3, oldest),
                operationRow(ids.get(5), 1, 1, oldest));
        String collection = "wallet_operations";
        var cleanup = Query.query(Criteria.where("_id").in(ids));

        Mono<Void> checks = operationRepository.findByTypeAndStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAscIdAsc(
                        WalletOperationMongoType.TOP_UP, WalletOperationMongoStatus.RECEIPT_RECORDED,
                        cutoff, PageRequest.ofSize(3))
                .map(row -> row.getId()).collectList()
                .doOnNext(batch -> assertThat(batch).containsExactly(first, ids.get(1), ids.get(2)))
                .thenMany(Flux.concat(
                        operationRepository.deferReceiptRecovery(first, oldest, deferred),
                        operationRepository.deferReceiptRecovery(first, oldest, deferred.plusSeconds(1)),
                        operationRepository.deferReceiptRecovery(ids.get(4), oldest, deferred),
                        operationRepository.deferReceiptRecovery(ids.get(5), oldest, deferred)))
                .collectList().doOnNext(counts -> assertThat(counts).containsExactly(1L, 0L, 0L, 0L))
                .then(operationRepository.findById(first))
                .switchIfEmpty(Mono.error(() -> new AssertionError("Deferred operation disappeared")))
                .doOnNext(row -> assertThat(row.getUpdatedAt()).isEqualTo(deferred))
                .thenMany(operationRepository.findByTypeAndStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAscIdAsc(
                        WalletOperationMongoType.TOP_UP, WalletOperationMongoStatus.RECEIPT_RECORDED,
                        cutoff, PageRequest.ofSize(1)))
                .map(row -> row.getId()).collectList()
                .doOnNext(batch -> assertThat(batch).containsExactly(ids.get(1))).then();

        StepVerifier.create(Mono.usingWhen(
                        mongo.execute(collection, target -> target.insertMany(rows)).single(), ignored -> checks,
                        ignored -> mongo.remove(cleanup, collection).then()))
                .expectComplete().verify(DEADLINE);
    }

    @Test
    void reclaimsALegacyJobLeaseAndFencesStaleRelease() {
        String collection = "wallet_job_leases";
        String job = "legacy-job-lease";
        String oldToken = "legacy-token";
        Duration duration = Duration.ofMinutes(1);
        Query id = Query.query(Criteria.where("_id").is(job));
        var legacy = new Document("_id", job).append("token", oldToken)
                .append("expiresAt", Date.from(Instant.now().plus(duration)));

        StepVerifier.create(mongo.execute(collection, rows -> rows.insertOne(legacy)).then(leases.acquire(job, duration)))
                .expectComplete().verify(DEADLINE);

        StepVerifier.create(mongo.updateFirst(id, new Update().set("expiresAt", Date.from(Instant.EPOCH)), collection)
                        .then(leases.acquire(job, duration))
                        .flatMap(token -> {
                            assertThat(token).isNotEqualTo(oldToken);
                            assertThat(UUID.fromString(token).version()).isEqualTo(7);
                            return leases.release(job, oldToken)
                                    .then(mongo.findOne(id, Document.class, collection))
                                    .doOnNext(row -> assertThat(row.getString("token")).isEqualTo(token))
                                    .flatMap(row -> leases.release(job, token).then(mongo.exists(id, collection)));
                        }))
                .expectNext(false).expectComplete().verify(DEADLINE);
    }

    @Test
    void expiredOutboxClaimCanBeReclaimedButCannotAcknowledgeANewerLease() {
        Mono<WalletOperationModel> operation = seedWallet("outbox-lease", "3")
                .then(mutations.spend("outbox-lease", "lease-key", BigDecimal.ONE, "EUR"))
                .map(WalletApplicationIT::successfulResult);
        StepVerifier.create(outbox.ensurePublishIndex()
                .then(operation).flatMap(result -> {
                    Query id = Query.query(Criteria.where("_id").is(result.getId()));
                    Mono<?> makeEarliest = mongo.updateFirst(id,
                            new Update().set("availableAt", Date.from(Instant.EPOCH)), "wallet_outbox");
                    return makeEarliest.then(outbox.claim(Duration.ofMinutes(1)))
                            .flatMap(first -> {
                                assertThat(first.getId()).isEqualTo(result.getId());
                                assertThat(UUID.fromString(first.getLeaseToken()).version()).isEqualTo(7);
                                return makeEarliest.then(outbox.claim(Duration.ofMinutes(1)))
                                        .flatMap(second -> {
                                            assertThat(second.getId()).isEqualTo(first.getId());
                                            assertThat(second.getLeaseToken()).isNotEqualTo(first.getLeaseToken());
                                            assertThat(UUID.fromString(second.getLeaseToken()).version()).isEqualTo(7);
                                            return outbox.acknowledge(first)
                                                    .doOnNext(ack -> assertThat(ack).isFalse())
                                                    .then(outbox.acknowledge(second));
                                        });
                            });
                }))
                .expectNext(true).expectComplete().verify(DEADLINE);
    }

    private WebTestClient.ResponseSpec postTopUp(String id, String key, String amount) {
        return webClient.post().uri(TOP_UP_PATH, id).contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_IDEMPOTENCY_KEY, key).bodyValue(topUpBody(amount, HTTP_TEST_CARD)).exchange();
    }

    private static String topUpBody(String amount, String card) {
        return "{\"amount\":%s,\"currency\":\"EUR\",\"provider\":\"stripe\",\"creditCardNumber\":\"%s\"}".formatted(amount, card);
    }

    private static Document operationRow(String id, int type, int status, Instant updatedAt) {
        return new Document("_id", id).append("walletId", "repository-reconciliation")
                .append("type", type).append("status", status).append("updatedAt", Date.from(updatedAt))
                .append("amount", new Decimal128(BigDecimal.ONE)).append("currency", "EUR");
    }

    private static <T> T presentResult(Outcome<Optional<T>, Error> outcome) {
        return successfulResult(outcome).orElseThrow(() -> new AssertionError("Expected a present result"));
    }

    private static <T> T successfulResult(Outcome<T, Error> outcome) {
        assertThat(outcome.getError()).isNull();
        assertThat(outcome.getData()).isNotNull();
        return outcome.getData();
    }

    private static void assertFailure(Outcome<?, Error> outcome, WalletErrorCode code) {
        assertThat(outcome.getData()).isNull();
        assertThat(outcome.getError()).isNotNull();
        assertThat(outcome.getError().getCode()).isEqualTo(code);
    }

    private Mono<Void> seedWallet(String id, String balance) {
        return mongo.insert(new Document("_id", id).append("currency", "EUR")
                .append("balance", new Decimal128(new BigDecimal(balance))), "wallets").then();
    }

    private Mono<Void> checkCommittedBalance(String id, String expectedBalance, String expectedDelta, int postings) {
        return Mono.zip(mongo.findById(id, Document.class, "wallets").single(),
                        mongo.find(Query.query(Criteria.where("walletId").is(id)), Document.class, "wallet_ledger").collectList())
                .doOnNext(state -> {
                    var wallet = state.getT1();
                    assertThat(wallet.get("balance", Decimal128.class).bigDecimalValue()).isEqualByComparingTo(expectedBalance);
                    assertThat(wallet.getLong("ledgerSequence")).isEqualTo((long) postings);
                    var ledger = state.getT2();
                    assertThat(ledger).hasSize(postings);
                    assertThat(ledger.stream().map(row -> row.get("delta", Decimal128.class).bigDecimalValue())
                            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(expectedDelta);
                }).then();
    }

    private Mono<Long> outboxCount(String walletId, int type) {
        return mongo.count(Query.query(Criteria.where("walletId").is(walletId).and("eventType").is(type)),
                "wallet_outbox");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MongoConfiguration {

        @Bean
        @ServiceConnection
        MongoDBContainer mongoContainer() {
            return new MongoDBContainer(System.getProperty("wallet.test.mongo.image", "mongo:8.3.2-noble"))
                    .withReplicaSet();
        }
    }
}
