# Wallet scenario runbook

Run commands from the repository root. Pick one environment: **checker** for automated end-to-end payments, **host demo** for manual requests, **base Compose** for startup and reads, or **Testcontainers** for isolated failure tests. They do not share wallets. Java commands require JDK 25 and Maven 3.9+. Docker must be running for Compose and integration tests.

## 1. Prove a charge, replay, decline and exact balance

```sh
python3 deployment/check-wallet.py
```

Requires Python 3 (assertions enabled) and Docker Compose; no separate build, database or `mongosh` installation. The script seeds €10, charges €5.25 and replays the same key: expect €15.25, one charge and one posting. It checks decimal boundaries, rejects invalid fixture amounts and currency, and repeats a declined request: expect one provider call, zero postings and unchanged balance. Success is exit code zero. Cleanup is automatic for this randomly named project; Maven artifacts remain.

## 2. Prove recovery after process death or a primary election

```sh
python3 deployment/check-wallet.py --ha
```

Use a recent Docker Compose supporting `!reset`. This runs the baseline and then interrupts **local settlement after receipt persistence** in a disposable replica set. On process death or primary step-down, same-key replay must settle once. A separate case kills the serving wallet and lets a reconciliation worker settle without client replay. Each must leave €15.25, one charge and one posting. The checker also reruns initialization with one member stopped. No manual cleanup normally needed. Do not reproduce these failpoints on a shared database. This is not evidence of recovery when a receipt never arrived.

## 3. Start, inspect and stop the default stack

```sh
docker compose -f deployment/compose.yaml up --build -d
curl --fail --retry 12 --retry-delay 3 --retry-all-errors http://127.0.0.1:8090/actuator/health/readiness
curl -fsS http://127.0.0.1:8090/api/v1/wallets/log
curl -i http://127.0.0.1:8090/api/v1/wallets/not-seeded
docker compose -f deployment/compose.yaml logs wallet simulator
```

Expect ready, a timestamp under `result.string`, and 404 for an absent wallet. `/log` checks only the listener. Top-up/discovery are not registered. If startup stalls, inspect `docker compose -f deployment/compose.yaml logs mongo-init wallet`: a fresh set needs all three members; an existing set needs a writable majority. Adjust the curl port if using `WALLET_HTTP_PORT`. For a host-only read service use the [build and launch instructions](wallet-reference.md#build-and-test) with an explicitly configured host-reachable replica set instead; base Compose publishes no database port.

Stop with `docker compose -f deployment/compose.yaml down` (retains volumes). Use `down -v` **only for a disposable project whose database you intend to erase**. For monitoring beyond JSON logs and readiness, see [available monitoring](local-monitoring.md).

## 4. Exercise HTTP outcomes by hand

First follow [interactive host setup](wallet-reference.md#try-a-local-top-up): start the simulator and wallet on loopback, seed `wallet-demo` once at €10, then use a third terminal for curl. Never use real card data or proxy the listener. In the table, **POST** means the exact synthetic-card curl request in that setup. Change only the fields specified; use a fresh key for each *new* scenario. Do not change keys to resolve an uncertain charge.

| Scenario | Action | Expected observation |
| --- | --- | --- |
| Lookup and discovery | GET `/api/v1/wallets/wallet-demo` and `/api/v1/wallets/payment-providers` | Wallet balance and currency; `stripe` is listed when enabled. Neither request charges. |
| Success and replay | POST €5.25; repeat unchanged, then GET the wallet | Both results `succeeded`; €15.25 after both, not €20.50. |
| Conflicting reuse | Repeat the successful POST with the same key but amount `5.26` | 409; original operation unchanged. |
| Invalid input / missing wallet | Omit the key, use a fresh key with USD on the EUR wallet, or submit a valid POST to an unknown ID | 400 for invalid input/currency; 404 for the absent wallet. No charge. |
| Confirmed decline | Restart **simulator only** with `--payment-simulator.scenario=REJECT`; POST with a fresh key, then replay it | Provider 422 becomes wallet 200 `payment_rejected`; wallet balance unchanged. |
| Timeout | Restart simulator with `--payment-simulator.scenario=TIMEOUT`; POST with a fresh key and replay the same request | Unconfirmed, not a decline or a credit. Expect 202 unresolved; do not send another charge. The delayed 504 fixture does not prove provider acceptance. |
| Standalone provider protocols | Run [Stripe charge](wallet-reference.md#stripe-charges), [PayPal order and capture](wallet-reference.md#paypal-orders-and-capture) or [Redsys authorization](wallet-reference.md#redsys-rest-authorization) directly against the simulator | Fixture-specific HTTP and response codes are documented with each command. Direct requests do **not** credit a wallet. |

Stop the foreground Java processes with Ctrl-C. Restart the simulator without the scenario argument to restore `SUCCESS`. For all HTTP result states, including `provider_unavailable`, see the [status table](wallet-reference.md#try-a-local-top-up).

## 5. Run tests by failure mode

`mvn test` runs unit tests. `mvn verify -Dwallet.test.mongo.image=mongo:8.3.17-noble` also runs integration tests; do not start Compose. Docker supplies disposable MongoDB, Kafka and Toxiproxy as needed. For a focused run, define this shell helper once in the current terminal; Maven still runs unit tests and narrows the Failsafe phase only:

```sh
wallet_it() {
  mvn -pl wallet -am verify "-Dit.test=$1" -Dwallet.test.mongo.image=mongo:8.3.17-noble "${@:2}"
}
```

| Scenario | Run | What must hold |
| --- | --- | --- |
| Invalid requests, replay, conflicts and uncertain HTTP result | `wallet_it WalletApplicationIT` | WebFlux and real Mongo, mocked payment adapter: reject invalid requests without dispatch; unresolved replay never charges again. |
| Five concurrent mutation cases | `wallet_it 'WalletApplicationIT#concurrent*'` | Same-key credit/debit apply once; admissible distinct credits both apply; racing debits cannot overdraw; two credits cannot reserve the last exact unit twice. |
| Hot wallet, four in flight | `wallet_it 'WalletApplicationIT#pressureConcurrent*' -Dwallet.pressure=true` | Forty €1 spends against €20 yield 20 successes and €0. Forty €1 credits yield €40, 40 charges, postings and credit events. Skipped normally; not a throughput benchmark. |
| Settlement rollback and receipt recovery | `wallet_it 'WalletApplicationIT#recordedReceiptSurvivesAFailedOutboxWriteAndCanBeAppliedLater'` | Failed settlement leaves the receipt durable; later local settlement succeeds without redispatch. |
| Scheduled reconciliation | `wallet_it 'WalletApplicationIT#reconciliationAppliesAReceiptAndFlagsAStaleChargeWithoutContactingStripe'` | Recorded receipt settles; stale unconfirmed operation goes to review. |
| Kafka balance-event delivery | `wallet_it WalletOutboxRelayIT` | Exercise the relay against a disposable broker. Broker acknowledgment precedes the Mongo acknowledgment; consumers must deduplicate. |
| Historical audit, network cut, discrepancy alert | `wallet_it WalletBalanceAuditIT` | A network cut leaves the cutoff due; after reconnection €6.75 agrees. A deliberately corrupt wallet (€11 vs €9) produces protected evidence and an opaque Kafka alert, not a balance repair. |

Expect Maven `BUILD SUCCESS`; inspect `wallet/target/failsafe-reports/` for the selected IT and skips. No persistent demo seed is needed. If the default Kafka image cannot run locally, append `-Dwallet.test.kafka.image=apache/kafka:4.0.0` to the Maven invocation.

## 6. Operator procedures (not acceptance tests)

Use only an authorized, explicitly named database. Base Compose has **no Kafka broker**. See [worker configuration](wallet-reference.md#worker-configuration) for validated lease, timeout and batch settings.

| Task | Action and expected result |
| --- | --- |
| Enable receipt recovery in Compose | `WALLET_RECONCILIATION_ENABLED=true docker compose -f deployment/compose.yaml up -d wallet`. Recorded receipts become eligible for local settlement; stale pending operations are flagged, not charged. Recreate with the flag false to disable. |
| Enable the balance-event relay | Provision `wallet.events.v1` (or `WALLET_OUTBOX_TOPIC`) on a reachable broker. On a wallet **process**, set `SPRING_KAFKA_BOOTSTRAP_SERVERS` and `WALLET_OUTBOX_ENABLED=true`. If using Compose, explicitly pass the outbox flag via an override; base Compose does not forward it. Expect at-least-once events keyed by wallet ID. Use the relay IT above for a disposable demonstration. |
| Enroll and audit a wallet | Follow [audit rollout and retention prerequisites](wallet-balance-audit.md#enroll-wallets-prospectively). With approved `AUDIT_WALLET_ID`, `AUDIT_CURRENCY` and `AUDIT_OPENING` exported and writers paused, run `mongosh "$WALLET_DB_URI" --file deployment/enroll-wallet-audit.js`. First cutoff is next 00:00 UTC. Agreement advances the schedule; investigate discrepancies, never edit ledger entries. |
| Upgrade legacy enum storage | Back up and stop **all** old writers and workers, then follow [offline migration](wallet-reference.md#upgrading-existing-mongodb-data). Start new writers only after validation and conversion succeed. Fresh databases skip this step. |

## Accepted charge with a missing receipt

Do not repeat the charge. A timeout or `review_required` is not evidence of acceptance or rejection. Obtain an authoritative provider record **uniquely linking this operation to this payment**, including provider receipt, amount and currency. A matching amount and timestamp are insufficient. If the provider cannot establish the link, leave the operation in review.

For a confirmed acceptance, retain the evidence and reviewer approval in a restricted case. On the intended wallet replica set, an authorized operator can set `OPERATION_ID`, `PROVIDER_RECEIPT`, `WALLET_ID`, `VERIFIED_AMOUNT`, `VERIFIED_CURRENCY` and `CASE_REFERENCE` from that case, then run `mongosh "$WALLET_MONGO_URI" --file deployment/record-confirmed-charge.js` (the URI must select the wallet database). Use an opaque case reference, never card data. The script accepts a Stripe top-up in review and atomically records its receipt; it does not move money. A stale pending payment must first enter review through reconciliation. On conflict, stop and investigate. After an uncertain commit read back the receipt and operation before proceeding.

Let the reconciliation worker apply the recorded receipt, or replay the original operation with the same key. Verify the operation is `succeeded`, its receipt belongs to that operation, and there is one matching credit posting and outbox event. Never edit the balance directly. The supplied simulator does not guarantee evidence lookup; this procedure does not make an unverifiable charge recoverable.

**Admission and rollout:** Top-ups reserve Decimal128 capacity against all unsettled credits, including those in review. This is a storage precision limit, not a production currency or minor-unit policy. Stop old mutation writers before deploying: they do not take the admission fence. The credit pressure test requires 40 provider calls, 40 credits, 40 postings, 40 credit events and a final balance of 40.