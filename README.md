# Playtomic wallet challenge

A player can look up an existing wallet and top it up by credit card. The wallet charges a separate Stripe-compatible HTTP simulator; MongoDB stores the balance. The challenge is to handle concurrent requests and payment failures without charging or crediting twice.

## Try both endpoints first

From the repository root, with **JDK 25, Maven 3.9+, Python 3 and running Docker with Compose**:

```sh
python3 deployment/check-wallet.py
```

![Two requests, one charge: €10 becomes €15.25 after a €5.25 top-up. Replaying the same key leaves €15.25, one provider charge and one ledger posting.](docs/top-up-replay.svg)

The checker builds both JARs, starts a private replica set, simulator and wallet, then acts as the HTTP client. No manual seed, running Compose stack, hosted payment service or real card is needed.

| Given | When | Expected result |
| --- | --- | --- |
| A wallet holding €10 | GET the wallet, POST €5.25, repeat with the same key, then GET again | €15.25, **one** provider charge and **one** ledger posting |
| A balance beyond JavaScript’s safe integer range | Credit one cent | The exact cent survives the HTTP round trip |
| A provider that declines | POST and replay the same key | `payment_rejected` both times, one provider call, no posting, balance still €10 |

A passing run exits zero and removes its containers and volumes. Add `--ha` to exercise process death, a MongoDB election and scheduled receipt recovery. **Ordinary Compose does not register the top-up route.** To assess both operations, use the checker above or the [interactive loopback demo](docs/wallet-reference.md#try-a-local-top-up).

## What is implemented?

| Operation | Availability | Contract |
| --- | --- | --- |
| `GET /api/v1/wallets/{id}` | Default and demo | Returns ID, balance and currency under `result`; missing wallet → 404. Lookup never provisions a wallet. |
| `POST /api/v1/wallets/{id}/top-ups` | Loopback demo only | Requires one `Idempotency-Key`, amount, currency, provider and the synthetic card. Only `result.status: succeeded` confirms credit. |
| `GET /api/v1/wallets/payment-providers` | Loopback demo only | Lists enabled top-up providers, not provider health. Only Stripe is wired for wallet charges. |

Spending and refunding purchases are interview discussion topics, not required HTTP routes. Spending exists internally; there is no public spend, refund or transaction-history API. The simulator demonstrates the provider boundary, not live payment processing or production authorization.

## Where to go next

- [Scenario runbook](docs/run-wallet.md) — start or stop the stack, exercise individual outcomes, run concurrency and fault checks, and follow operator procedures.
- [Software Architecture Document (SAD)](docs/SAD.md) — components, transaction boundaries, recovery decisions and production limits.
- [API, simulator and configuration reference](docs/wallet-reference.md) — requests, exact status mapping, provider fixtures and worker settings.
- [Balance audit](docs/wallet-balance-audit.md) · [Logging](docs/wallet-logging.md) · [Local monitoring](docs/local-monitoring.md).

For unit tests use `mvn test`. For unit **and** integration tests use `mvn verify -Dwallet.test.mongo.image=mongo:8.3.17-noble` with Docker running; Testcontainers creates its own services. Maven builds `common` (library), `wallet` (service) and `simulator` (service). The legacy root `src/` is not built.