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

- `GET /{id}` returns a `ResponseDtoV1` envelope, with the wallet under
  `result` on success and an `error` on lookup failure. The HTTP status is
  `200`, `400`, `404`, or `500` as appropriate. For example, a wallet with ID
  `wallet-123` is looked up at `GET /wallet-123`.
- `GET /log` is the smoke endpoint. Neither `/wallets/{id}` nor
  `/api/v1/wallets/{id}` is currently mapped.

Write operations are currently internal service methods.

## Run with Docker Compose

From the repository root:

```sh
docker compose -f deployment/compose.yaml up --build -d
curl --fail --retry 12 --retry-delay 3 --retry-all-errors http://127.0.0.1:8090/log
docker compose -f deployment/compose.yaml down
```

Compose builds separate `wallet` and `simulator` images and runs them as
separate services alongside the MongoDB replica set. The wallet defaults to
`http://simulator:9999` for all payment provider endpoints on the Compose
network. The simulator port is not published to the host. `/log` checks the
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
fixture is used for wallet top-ups. No new routes or refund workflows are exposed.

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
- Wallet operation timers and result counters use bounded tags. Workers log
  sanitized failures and stale payments that need review. Alert routing and
  trace export depend on the deployment's observability stack.

The public write contract, provider currency and amount policy, and any
separate immutable ledger are not defined by these internal methods.

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