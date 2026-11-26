# Software Architecture Document: wallet service

**Scope:** the implemented proof of concept, not a proposed production platform. The required operations are lookup and card top-up. The design prioritizes the integrity of money over automatic completion: a delayed credit can be investigated; a second charge is not a safe way to resolve an unknown first one.

## Context and deployment

```text
Client ── HTTP v1 ──> wallet (WebFlux) ── non-retrying HTTP ──> payment simulator
                        │                                         (Stripe fixture)
                        ├── reactive reads / transactions ──> MongoDB replica set
                        └── optional outbox workers ────────> Kafka ──> consumers
```

MongoDB is authoritative for wallet balances and recorded operations; only the payment provider can confirm a remote charge. The `wallet` and `simulator` Maven modules produce separate executables and images. `common` is a library for shared contracts, mapping, errors and instrumentation, not a network service. Base Compose supplies three Mongo members, no broker, and a wallet port published on host loopback. The acceptance demo uses a private loopback interface for both Java processes and its client. The simulator’s PayPal and Redsys routes are independent fixtures, not fallback charge paths.

## Components and responsibilities

| Boundary | Responsibility |
| --- | --- |
| `WalletControllerV1` → lookup or mutation service | Validate HTTP input, invoke the use case, map the domain outcome to a v1 envelope and status. Lookup is read-only; it never reserves an operation or calls Stripe. |
| `WalletMutationService` → `WalletOperationService` | Coordinate idempotency, provider admission, receipt recording and local settlement. The business service chooses what must commit together. |
| `StripeAdapter` → `StripeClient` | Convert wallet models to provider DTOs and classify provider outcomes; the client owns HTTP. No payment retry, including on timeout. |
| Entity services → repositories / `WalletMongoTransactions` | Map persistence entities to business models, enforce guarded mutations, and execute bounded local transactions. No JVM lock is relied on for cross-instance correctness. |
| Reconciliation, event relay and balance auditor | Opt-in background work. A lease token or claim token prevents an expired worker from committing or acknowledging work on behalf of its replacement. |

## Runtime view: a €5.25 top-up

1. **Reserve.** Validate the existing wallet and currency. Durably bind the wallet/key to the operation type, amount, currency and provider. Only the reservation winner may dispatch. A different intent under the same key conflicts; a matching replay uses the existing operation. Admission accounts for unsettled credits so that the resulting balance can still be stored exactly as Decimal128.
2. **Charge outside MongoDB.** Send the card and amount once. A confirmed 422 records rejection. An ambiguous outcome is not a decline: keep it unresolved for review. Never store the card number on the operation or log the request body.
3. **Record the receipt.** Commit the provider payment ID and its ownership separately from crediting. This durable boundary allows another instance to finish crediting without contacting the provider.
4. **Settle locally.** In one transaction, update the balance, append the sequenced ledger posting, finalize the operation with its resulting balance and insert the outbox event. A rollback leaves no partial credit. Retry eligible transient *local* transaction failures, not an unknown commit result or any chain containing a charge.
5. **Respond or replay.** A completed replay returns the recorded result, not a newly computed current balance. A durable receipt may resume local settlement. HTTP 200 alone is not proof of credit: inspect `result.status`. See the [wire contract](wallet-reference.md#try-a-local-top-up).

## Failure decisions

| Observation | Safe action | Why |
| --- | --- | --- |
| Two instances receive the same wallet/key | Resolve the durable reservation; do not dispatch twice | Request coordination must survive process death and cannot depend on an in-memory lock. |
| Provider confirms rejection | Persist `payment_rejected`; no posting | A replay must not turn the recorded decline into a fresh payment. |
| Timeout, lost response, or abandoned pending operation with no receipt | Reconcile stale state to review; require uniquely attributable provider evidence | Absence of a receipt is not evidence of absence of a charge. Automatic completion is not guaranteed. |
| Process dies or Mongo elects a primary after the receipt commits | Replay the original key or run reconciliation | Only local settlement remains. The worker never charges. |
| Kafka accepts an event but the relay cannot acknowledge it in Mongo | Republish after claim expiry; consumer deduplicates by `eventId` | Delivery is at least once, not exactly once. Broker availability does not define whether a credit committed. |
| Historical audit disagrees or the snapshot is unavailable | Preserve discrepancy evidence and block that wallet, or leave the cutoff due if unavailable | Never substitute today's balance for a historical snapshot or silently repair money. |

## Data and design trade-offs

Opaque string wallet IDs are distinct from operation IDs (derived from wallet and idempotency key) and receipt IDs (assigned by the provider). Java decimal arithmetic and BSON Decimal128 preserve exactness; v1 JSON money fields are numbers and clients must parse them as decimals. The fixture’s EUR/cents restrictions govern new Stripe charges, not every wallet balance. Internal spend checks sufficient funds as part of the database mutation. There is no public provisioning, spend, purchase-refund or history endpoint.

The extra receipt transaction costs a write but separates a fact that cannot be rolled back (provider acceptance) from a credit that can. Outbox persistence adds storage and consumer deduplication but avoids a Mongo/Kafka dual write on the request path. Per-wallet serialization and scanning unsettled credits favor correctness over hot-wallet throughput. The pressure tests establish bounded invariants, not a capacity guarantee.

## Operational and production boundary

Top-up and discovery require the loopback-only demo profile and synthetic card. There is no production owner authorization for either initial dispatch or replay. Production would need a tokenized payment-data design, TLS, provider evidence or reconciliation agreements, approved currency and amount policies, and measured capacity and recovery objectives. The auditor also requires verified opening balances and retained Mongo snapshot history. JSON logs and readiness exist; dashboards, Kafka infrastructure and pager delivery are not supplied by base Compose.

**Verification:** use the [scenario runbook](run-wallet.md) for packaged HTTP acceptance, replica-set races, crash/election recovery, Kafka delivery and audit faults. These checks have different boundaries: a test with a mocked provider cannot establish how a live payment network behaves.

