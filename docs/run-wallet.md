# Run the wallet and its tests

Run these commands from the repository root with JDK 25 and Maven 3.9+. Docker must be running for Compose and integration tests. Testcontainers creates its own disposable services; do **not** start Compose for tests.

| Task | Command |
| --- | --- |
| Run the wallet on the host | `mvn -pl wallet -am package -DskipTests && java -jar wallet/target/exercise-wallet-0.0.3.jar` |
| Start the local stack (wallet, simulator, MongoDB replica set) | `docker compose -f deployment/compose.yaml up --build -d` |
| Smoke-check either running wallet | `curl --fail http://127.0.0.1:8090/api/v1/wallets/log` |
| Stop the stack, retaining data | `docker compose -f deployment/compose.yaml down` |
| Run unit tests only | `mvn test` |
| Run unit and integration tests | `mvn verify -Dwallet.test.mongo.image=mongo:8.3.17-noble` |
| Run the Toxiproxy balance audit and Kafka alert checks | `mvn -pl wallet -am verify -Dit.test=WalletBalanceAuditIT -Dwallet.test.mongo.image=mongo:8.3.17-noble` |
| Focus integration coverage on the four double-operation races | `mvn -pl wallet -am verify '-Dit.test=WalletApplicationIT#concurrent*' -Dwallet.test.mongo.image=mongo:8.3.17-noble` |
| Run the opt-in hot-wallet pressure test | `mvn -pl wallet -am verify '-Dit.test=WalletApplicationIT#pressureConcurrentSpendsOnOneWallet' -Dwallet.pressure=true -Dwallet.test.mongo.image=mongo:8.3.17-noble` |
| Run both opt-in hot-wallet pressure tests | `mvn -pl wallet -am verify '-Dit.test=WalletApplicationIT#pressureConcurrent*' -Dwallet.pressure=true -Dwallet.test.mongo.image=mongo:8.3.17-noble` |

**Host or Compose:** Choose one; both use port 8090 by default. The host wallet needs a writable MongoDB replica set reachable via `SPRING_MONGODB_URI`. Compose provides its own internal replica set, not a database port on the host. Top-up HTTP is disabled by default; see the [loopback-only demo](../README.md#try-a-local-top-up).

**What the focused tests prove:** Same-key top-ups charge and credit once; different keys credit both amounts. Same-key spends debit once even when both are affordable; two different keys competing for the last funds cannot overdraw. These tests exercise the mutation service against real MongoDB and mock the payment adapter; spend has no HTTP endpoint.

**Pressure test:** Forty distinct €1 spends compete for €20 in one wallet, up to four in flight. The test requires 20 successes, 20 insufficient-funds results, a zero stored balance, and exactly 20 ledger postings and debit events. This is a bounded correctness check, not a throughput benchmark. Normal `verify` skips it. The focused commands still run unit tests; `it.test` narrows the Failsafe integration phase only.

**Network faults and audit alerts:** The audit suite routes MongoDB traffic through a disposable `ghcr.io/shopify/toxiproxy:2.12.0`. With 60 ms downstream latency, a €3.25 debit from €10 leaves €6.75. Cutting the connection before the audit must leave the cutoff pending; after reconnection, replay must leave one posting and the audit must confirm €6.75 without queuing an alert. A separate deliberately corrupted wallet (€11 stored vs €9 calculated) must produce a redacted discrepancy log, one `wallet_balance_audit` document, and a Kafka alert linked by event ID. The test consumes that alert from `wallet.audit.alerts.v1`; delivery to a downstream pager or dashboard is outside this check.

## Accepted charge with a missing receipt

Do not repeat the charge. A timeout or `review_required` is not evidence of acceptance or rejection. Obtain an authoritative provider record **uniquely linking this operation to this payment**, including provider receipt, amount and currency. A matching amount and timestamp are insufficient. If the provider cannot establish the link, leave the operation in review.

For a confirmed acceptance, retain the evidence and reviewer approval in a restricted case. On the intended wallet replica set, an authorized operator can set `OPERATION_ID`, `PROVIDER_RECEIPT`, `WALLET_ID`, `VERIFIED_AMOUNT`, `VERIFIED_CURRENCY` and `CASE_REFERENCE` from that case, then run `mongosh "$WALLET_MONGO_URI" --file deployment/record-confirmed-charge.js` (the URI must select the wallet database). Use an opaque case reference, never card data. The script accepts a Stripe top-up in review and atomically records its receipt; it does not move money. A stale pending payment must first enter review through reconciliation. On conflict, stop and investigate. After an uncertain commit read back the receipt and operation before proceeding.

Let the reconciliation worker apply the recorded receipt, or replay the original operation with the same key. Verify the operation is `succeeded`, its receipt belongs to that operation, and there is one matching credit posting and outbox event. Never edit the balance directly. The supplied simulator does not guarantee evidence lookup; this procedure does not make an unverifiable charge recoverable.

**Admission and rollout:** Top-ups reserve Decimal128 capacity against all unsettled credits, including those in review. This is a storage precision limit, not a production currency or minor-unit policy. Stop old mutation writers before deploying: they do not take the admission fence. The credit pressure test requires 40 provider calls, 40 credits, 40 postings, 40 credit events and a final balance of 40.