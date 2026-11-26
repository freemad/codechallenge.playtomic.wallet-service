# Wallets Service
In Playtomic, we have a service to manage our wallets. Our players can top-up their wallets using a credit card and spend that money on our platform (bookings, racket rentals, ...)

That service has the following operations:
- You can query your balance.
- You can top-up your wallet. In this case, we charge the amount using a third-party payments platform (stripe, paypal, redsys).
- You can spend your balance on purchases in Playtomic. 
- You can return these purchases, and your money is refunded.
- You can check your history of transactions.

This exercise consists of building a proof of concept of that wallet service.
You have to code endpoints for these operations:
1. Get a wallet using its identifier.
1. Top-up money in that wallet using a credit card number. It has to charge that amount internally using a third-party platform.

You don't have to write the following operations, but we will discuss possible solutions during the interview:
1. How to spend money from the wallet.
1. How to refund that money.

The basic structure of a wallet is its identifier and its current balance. If you think you need extra fields, add them. We will discuss it in the interview. 

## Build and test

Use JDK 25 and Maven 3.9+. From the repository root, `mvn test` runs the unit
suite and `mvn verify` also runs the wallet integration tests in Docker. They
use a disposable MongoDB replica set and, for the enabled outbox relay test,
a real Kafka broker (`apache/kafka-native:4.0.0` by default; override with
`-Dwallet.test.kafka.image`). If the pinned MongoDB test image cannot run on
the host kernel, use the test-only override:

```sh
mvn verify -Dwallet.test.mongo.image=mongo:8.3.17-noble
```

The reactor builds the `common` library, the `wallet` service, and the local
payment `simulator`. Legacy sources under the root `src/` are outside the
reactor. After packaging, run
`java -jar wallet/target/exercise-wallet-0.0.3.jar` with `SPRING_MONGODB_URI`
pointing to a writable MongoDB replica set.

- `GET /api/v1/wallets/{id}` returns a `ResponseDtoV1` envelope, with the wallet under
  `result` on success and an `error` on lookup failure. The HTTP status is
  `200`, `400`, `404`, or `500` as appropriate. For example, a wallet with ID
  `wallet-123` is looked up at `GET /api/v1/wallets/wallet-123`.
- `GET /api/v1/wallets/log` is the smoke endpoint. The previous unversioned URLs
  are not mapped. The `log` and `payment-providers` segments are reserved and
  are not wallet lookup IDs on this API.

Spend and other writes remain internal; top-up is HTTP-accessible only in the
opt-in local demo below.

## Try a local top-up

`POST /api/v1/wallets/{id}/top-ups` is disabled by default, including in the default
Compose stack. For this demo, run both Java services on the same host. Start
[the local payment simulator](#local-payment-simulator) and use a disposable,
writable MongoDB replica set reachable from the host. The unmodified Compose
stack does not publish MongoDB or the simulator to the host.

Set `SPRING_MONGODB_URI` to the disposable replica set (the profile defaults to
`mongodb://localhost:27017/playtomic?directConnection=true&replicaSet=wallet-rs&w=majority`).
On a fresh disposable database, seed a wallet and start the wallet JAR:

```sh
mongosh "$SPRING_MONGODB_URI" --eval 'db.wallets.insertOne({_id:"wallet-demo", balance:NumberDecimal("10.00"), currency:"EUR"})'
java -jar wallet/target/exercise-wallet-0.0.3.jar --spring.profiles.active=local-top-up
```

`WalletControllerV1` declares both demo routes. They are registered only when
`wallet.top-up.http.enabled` is `true`. It defaults to `false`; the `local-top-up`
profile enables it and sets `server.address` to `127.0.0.1`. Startup rejects an
attempt to enable it outside that profile or on another listener address.
The profile supplies `wallet.top-up.http.synthetic-card-number`, which must be
the documented synthetic card. Stripe charges must use the approved loopback
endpoint. Do not expose this profile through a proxy or use real card data.
This demo has no wallet-owner authorization.

```sh
curl -i http://127.0.0.1:8090/api/v1/wallets/payment-providers
```

Choose an ID from `result.providers`. This list contains enabled providers
wired into the wallet's top-up flow; it does not probe provider health.
Currently only `stripe` can appear. To disable it, set
`WALLET_PAYMENT_PROVIDERS_STRIPE_TOP_UP_ENABLED=false`.

```sh
curl -i http://127.0.0.1:8090/api/v1/wallets/wallet-demo/top-ups \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: wallet-demo-top-up-1' \
  --data '{"amount":5.25,"currency":"EUR","provider":"stripe","creditCardNumber":"4242424242424242"}'
```

Supply exactly one nonblank `Idempotency-Key`, a positive amount exactly
representable as Decimal128, the wallet's stored currency, and a provider ID.
Inspect `result.status`, not just the HTTP status or envelope `success` flag:

| HTTP status | Meaning |
| --- | --- |
| `200` | Recorded result: `succeeded`, `payment_rejected`, or `provider_unavailable`. Only `succeeded` includes `resultingBalance`. |
| `202` | Unresolved: `pending`, `receipt_recorded`, or `review_required`. This does not confirm credit. |
| `400` / `404` | Invalid request (including currency mismatch or a provider not enabled for a new top-up) / wallet not found. |
| `409` | The same wallet/key already identifies a different amount, currency, operation type, or provider. |
| `500` | Unexpected failure; internal details are not returned. |

If the response is lost or ambiguous, replay the same request with the same
key. The replay returns the recorded operation state, not the current wallet
balance, and does not dispatch a second charge. Do not use a new key to retry an
uncertain payment.

## Run with Docker Compose

From the repository root:

```sh
docker compose -f deployment/compose.yaml up --build -d
curl --fail --retry 12 --retry-delay 3 --retry-all-errors http://127.0.0.1:8090/api/v1/wallets/log
docker compose -f deployment/compose.yaml down
```

Compose builds separate `wallet` and `simulator` images and runs them as
separate services alongside the MongoDB replica set. The wallet defaults to
`http://simulator:9999` for all payment provider endpoints on the Compose
network. The simulator port is not published to the host. `/api/v1/wallets/log` checks the
wallet HTTP endpoint; it does not send a payment. Set
`PAYMENT_SIMULATOR_SCENARIO=REJECT` or `TIMEOUT` before starting Compose to use a
different fixture scenario. Use synthetic payment data only.

## Local payment simulator

The simulator needs no MongoDB or Kafka. It listens on `127.0.0.1:9999` by
default and serves local Stripe, PayPal, and Redsys fixtures. Use synthetic
payment data only. Build and run it in a separate terminal:

```sh
mvn -pl simulator -am package
java -jar simulator/target/payment-simulator-0.0.3.jar
```

For a wallet running on the same host, point its Stripe client at this process
instead of the hosted sandbox:

```sh
export WALLET_PAYMENT_PROVIDERS_STRIPE_CHARGES_URI=http://127.0.0.1:9999/v1/stripe-simulator/charges
export WALLET_PAYMENT_PROVIDERS_STRIPE_REFUNDS_URI='http://127.0.0.1:9999/v1/stripe-simulator/payments/{payment_id}/refunds'
```

### Wallet payment providers

In `wallet/src/main/resources/application.yaml`, `wallet.payment-providers` is
keyed by `stripe`, `paypal`, and `redsys`. Each provider has its own managed,
non-retrying HTTP client, with a `2s` connect timeout and a `5s` deadline per
HTTP request by default. Set `WALLET_PAYMENT_PROVIDERS_<PROVIDER>_CONNECT_TIMEOUT`
or `WALLET_PAYMENT_PROVIDERS_<PROVIDER>_REQUEST_TIMEOUT` to override either one
for `STRIPE`, `PAYPAL`, or `REDSYS`.

| Provider | Endpoint overrides |
| --- | --- |
| Stripe | `WALLET_PAYMENT_PROVIDERS_STRIPE_CHARGES_URI`, `WALLET_PAYMENT_PROVIDERS_STRIPE_REFUNDS_URI` |
| PayPal | `WALLET_PAYMENT_PROVIDERS_PAYPAL_ORDERS_URI` |
| Redsys | `WALLET_PAYMENT_PROVIDERS_REDSYS_AUTHORIZATION_URI` |

Outside Compose, Stripe defaults to the hosted sandbox in `develop` and
`http://localhost:9999` for charges in `test`. PayPal and Redsys default to the
local fixture routes. The old `STRIPE_SIMULATOR_CHARGESURI`,
`STRIPE_SIMULATOR_REFUNDSURI`, and `WALLET_SIMULATOR_*` environment variables are
still accepted as fallbacks in the wallet; the provider-specific overrides take
precedence.

`PayPalAdapter` creates and captures EUR orders. `RedsysAdapter` uses the unsigned
EUR authorization test protocol, not the production Redsys protocol. Neither
fixture is used for wallet top-ups. No refund workflows are exposed.

Compose uses the service DNS name by default. For other container deployments,
set the endpoint overrides to addresses reachable from the wallet container.
Use synthetic payment data and fixture endpoints only.

### Stripe charges

Wallet top-ups call `StripeAdapter`, which maps wallet models to and from typed
Stripe payloads. `StripeClient` sends the HTTP request without automatic retries.
`StripeService` remains a compatibility facade. A confirmed `422` is a rejection;
a timeout or missing receipt requires review, not another charge.

Send a sample charge directly to the simulator:

```sh
curl -i http://127.0.0.1:9999/v1/stripe-simulator/charges \
  -H 'Content-Type: application/json' \
  --data '{"credit_card":"4242424242424242","amount":15.25}'
```

Stripe returns `200` with a synthetic `id` in `SUCCESS` mode or `422` in
`REJECT` mode. The wallet still requires a configured refund URI, but the
simulator does not implement refunds.

### PayPal orders and capture

```sh
curl -i http://127.0.0.1:9999/v1/paypal-simulator/v2/checkout/orders \
  -H 'Content-Type: application/json' \
  --data '{"intent":"CAPTURE","purchase_units":[{"amount":{"currency_code":"EUR","value":"15.25"}}]}'
```

The fixture skips buyer approval and returns `201 APPROVED`. Set `ORDER_ID`
to the returned `id`, then capture the order:

```sh
curl -i -X POST "http://127.0.0.1:9999/v1/paypal-simulator/v2/checkout/orders/${ORDER_ID}/capture"
```

Success returns `201 COMPLETED` with a capture ID and amount under
`purchase_units[0].payments.captures[0]`. Decline returns `422` with
`INSTRUMENT_DECLINED` under `details[0].issue`. An unknown order returns `404`;
a second capture of a completed order returns `422 ORDER_ALREADY_CAPTURED`.
The fixture accepts one EUR purchase unit with a positive decimal string
(up to ten whole digits and two fractional digits). It retains up to 1,024
orders in memory; at capacity creation returns `503` until restart.

### Redsys REST authorization

```sh
params=$(printf '%s' '{"DS_MERCHANT_ORDER":"1234TEST","DS_MERCHANT_AMOUNT":"1525","DS_MERCHANT_CURRENCY":"978","DS_MERCHANT_TRANSACTIONTYPE":"0"}' \
  | base64 | tr '/+' '_-' | tr -d '=\n')
curl -i http://127.0.0.1:9999/v1/redsys-simulator/sis/rest/trataPeticion \
  -H 'Content-Type: application/json' \
  --data "$(printf '{"Ds_MerchantParameters":"%s","Ds_SignatureVersion":"SIMULATOR_ONLY","Ds_Signature":"SIMULATOR_UNSIGNED"}' "$params")"
```

The request uses Base64URL-encoded JSON, EUR numeric code `978`, and a positive
integer string in minor units (up to 12 digits). The order is four digits
followed by up to eight alphanumeric characters. Both approval and decline
return HTTP `200`: decode the returned `Ds_MerchantParameters` to read
`Ds_Response` (`0000` with `Ds_AuthorisationCode` on approval, `0101` on decline).
Malformed fixture requests return `400`. The signature fields are fixed fixture
markers, not cryptographic signatures.

### Scenarios and limits

`SUCCESS` is the default. Restart with `--payment-simulator.scenario=REJECT` for
declines, or `--payment-simulator.scenario=TIMEOUT` to delay payment responses
by ten seconds before returning `504`. PayPal order creation works in every mode;
the scenario applies only to capture. Timeout does not simulate an accepted
payment with a lost receipt. There are no real credentials, external provider
calls, OAuth, callbacks, redirects, or 3-D Secure. The wallet still uses Stripe
only; the PayPal and Redsys endpoints are standalone fixtures.

## Wallet write path

Top-up reserves a wallet-scoped idempotency key before calling the payment provider.
Only the reservation winner dispatches. An unconfirmed charge goes to review
instead of being retried; a recorded receipt can be applied locally without
contacting the provider again. Spending uses a sufficient-funds guard. The
balance change, operation result, and outbox event commit together. Replays
return the recorded result, not the current wallet balance.

- `WALLET_RECONCILIATION_ENABLED=true` enables lease-led recovery and stale
  payment review. The job never retries the provider call.
- `WALLET_OUTBOX_ENABLED=true` enables the relay. Set
  `SPRING_KAFKA_BOOTSTRAP_SERVERS` and provision `wallet.events.v1` (override
  with `WALLET_OUTBOX_TOPIC`). Events use the wallet ID as the Kafka key.
  Delivery is at least once: consumers must deduplicate by `eventId` and
  must not assume Mongo commit order across concurrent relay workers.
- Wallet operation timers and result counters use bounded tags. See the
  [wallet logging contract](docs/wallet-logging.md) for JSON fields, event levels,
  redaction rules, and how to enable wallet-only debug logging. Alert routing and
  trace export depend on the deployment's observability stack.

A production write contract is not defined. Production top-up would require
wallet-owner authorization before both initial dispatch and replay, TLS, and
an approved payment-data design (such as tokenization). Provider currency and
amount policy and adoption of a separate immutable ledger remain open.

### Worker configuration

Both workers are disabled by default. The wallet's `application.yaml` defines
these defaults and binds them to validated `wallet.reconciliation` and
`wallet.outbox` settings.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `WALLET_RECONCILIATION_INTERVAL` | `15s` | Fixed delay between runs |
| `WALLET_RECONCILIATION_LEASE` | `1m` | Exclusive job lease |
| `WALLET_RECONCILIATION_RUN_TIMEOUT` | `25s` | Timeout for work under the lease |
| `WALLET_RECONCILIATION_STALE_PENDING_AGE` | `2m` | Minimum pending age before review |
| `WALLET_RECONCILIATION_RECEIPT_DELAY` | `15s` | Minimum receipt age before local recovery |
| `WALLET_RECONCILIATION_BATCH_SIZE` | `25` | Maximum operations per status per run |
| `WALLET_OUTBOX_INTERVAL` | `2s` | Fixed delay between runs |
| `WALLET_OUTBOX_TOPIC` | `wallet.events.v1` | Kafka destination |
| `WALLET_OUTBOX_CLAIM_LEASE` | `45s` | Exclusive event claim |
| `WALLET_OUTBOX_SEND_TIMEOUT` | `20s` | Timeout for publishing an event |
| `WALLET_OUTBOX_BATCH_SIZE` | `25` | Maximum claims per run |

Settings are validated at startup even when the workers are disabled. Use
positive durations with explicit units and positive batch sizes. The job lease
must exceed the run timeout. The outbox claim lease must exceed the send timeout;
leave enough time for the database acknowledgment after broker success.

## Original exercise scaffold

So you can focus on these problems, you have here a maven project with a Spring Boot application. It already contains
the basic dependencies and in-memory version of MongoDB for testing. There are development and test profiles.
You will need to start a MongoDB instance to run the application. For example, using
```
docker run -d --name mongo4.0-rs -p 27017:27017 mongo:4.0
```

You can also find an implementation of the service that would call to the real payments platform (StripePaymentService).
This implementation is calling to a simulator deployed in one of our environments. Take into account
that this simulator will return 422 http error codes under certain conditions.

Consider that this service must work in a microservices environment in high availability. You should care about concurrency too.

You can spend as much time as you need but we think that 4 hours is enough to show [the requirements of this job.](OFFER.md)
You don't have to document your code, but you can write down anything you want to explain or anything you have skipped.
You don't need to write tests for everything, but we would like to see different types of tests.

### Upgrading existing MongoDB data

`wallet_operations.type`, `wallet_operations.status`, and `wallet_outbox.eventType`
use stable BSON int32 codes. HTTP statuses and Kafka event labels are unchanged.
For a database written by an older wallet, stop **all** wallet instances and workers,
back up the database, then run `mongosh "$WALLET_DB_URI" --file deployment/migrate-enum-values.js`
with the wallet database explicitly named in the URI. The script validates existing
values before writing and can be rerun after interruption. Start the new wallet only
when it completes successfully: numeric filters do not match old string values.
Fresh databases need no migration. Do not run old and new writers together.