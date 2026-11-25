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

The reactor builds the `common` library and the `wallet` service. Legacy
sources under the root `src/` are outside the reactor. After packaging, run
`java -jar wallet/target/exercise-wallet-0.0.3.jar` with `SPRING_MONGODB_URI`
pointing to a writable MongoDB replica set.

- `GET /{id}` returns a `ResponseDtoV1` envelope, with the wallet under
  `result` on success and an `error` on lookup failure. The HTTP status is
  `200`, `400`, `404`, or `500` as appropriate. For example, a wallet with ID
  `wallet-123` is looked up at `GET /wallet-123`.
- `GET /log` is the smoke endpoint. Neither `/wallets/{id}` nor
  `/api/v1/wallets/{id}` is currently mapped.

Write operations are currently internal service methods.

## Wallet write path

Top-up reserves a wallet-scoped idempotency key before calling the simulator.
Only the reservation winner dispatches. An unconfirmed charge goes to review
instead of being retried; a recorded receipt can be applied locally without
contacting the simulator again. Spending uses a sufficient-funds guard. The
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

The public write contract, simulator currency and amount policy, and any
separate immutable ledger are not defined by these internal methods.

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