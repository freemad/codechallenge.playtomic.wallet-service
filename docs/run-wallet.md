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
| Focus integration coverage on the four double-operation races | `mvn -pl wallet -am verify '-Dit.test=WalletApplicationIT#concurrent*' -Dwallet.test.mongo.image=mongo:8.3.17-noble` |
| Run the opt-in hot-wallet pressure test | `mvn -pl wallet -am verify '-Dit.test=WalletApplicationIT#pressureConcurrentSpendsOnOneWallet' -Dwallet.pressure=true -Dwallet.test.mongo.image=mongo:8.3.17-noble` |

**Host or Compose:** Choose one; both use port 8090 by default. The host wallet needs a writable MongoDB replica set reachable via `SPRING_MONGODB_URI`. Compose provides its own internal replica set, not a database port on the host. Top-up HTTP is disabled by default; see the [loopback-only demo](../README.md#try-a-local-top-up).

**What the focused tests prove:** Same-key top-ups charge and credit once; different keys credit both amounts. Same-key spends debit once even when both are affordable; two different keys competing for the last funds cannot overdraw. These tests exercise the mutation service against real MongoDB and mock the payment adapter; spend has no HTTP endpoint.

**Pressure test:** Forty distinct €1 spends compete for €20 in one wallet, up to four in flight. The test requires 20 successes, 20 insufficient-funds results, a zero stored balance, and exactly 20 ledger postings and debit events. This is a bounded correctness check, not a throughput benchmark. Normal `verify` skips it. The focused commands still run unit tests; `it.test` narrows the Failsafe integration phase only.
