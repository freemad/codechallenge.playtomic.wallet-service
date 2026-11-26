# Wallet diagnostic logs

The wallet's `logback-spring.xml` writes one Logstash JSON record per logging event to stdout. Filter by `logger_name=wallet.events` for the wallet contract below. Framework and shared-library messages use the same encoder but do not implement this event schema. Both root and wallet events default to INFO; set `LOGGING_LEVEL_WALLET_EVENTS=DEBUG` for diagnosis without enabling HTTP wire or database driver debug logs. TRACE has no wallet events.

Each wallet event has `@timestamp`, `level`, `logger_name`, `thread_name`, `message` and `event`. The message is the stable event name, not formatted request data. Fields are top-level JSON properties. For example, `operation.finalized` with `operationType=top_up` and `status=succeeded` means the credit and outbox entry committed together.

| Level | Events | Meaning |
|---|---|---|
| DEBUG | `http.response`, `operation.replay`, `operation.conflict`, `mutation.rejected` | HTTP status selected below 500, recorded operation reused, conflicting idempotency key, or expected business/input failure. |
| DEBUG | `transaction.retry`, `outbox.broker_accepted` | A local Mongo retry is scheduled, or Kafka returned a send result. Neither is a payment retry or a delivery acknowledgment in Mongo. |
| INFO | `operation.reserved`, `payment.dispatch`, `payment.receipt_returned`, `payment.declined` | New reservation, provider adapter invocation, receipt returned (not yet durable), or classified decline. |
| INFO | `receipt.committed`, `operation.finalized`, `outbox.acknowledged` | New receipt committed, apply transaction finalized a new result (inspect `status`), or the relay's fenced acknowledgment succeeded. Replays do not emit a second commit. |
| WARN | `payment.not_permitted`, `payment.unconfirmed`, `payment.cancelled`, `receipt.duplicate` | Breaker refused dispatch, result unknown, provider subscription cancelled, or receipt transaction hit a duplicate key. Cancellation does not prove the provider did not charge. |
| WARN | `reconciliation.review`, `reconciliation.apply_failed`, `outbox.lost_lease`, `outbox.delivery_incomplete` | Stale operation observed in review, local apply failed before deferral, acknowledgment lost its fence, or send/ack did not finish. Redelivery is possible. |
| ERROR | `mutation.failed`, `http.server_error`, `reconciliation.run_failed`, `outbox.run_failed` | Infrastructure/business-boundary failure, HTTP 5xx selected, or worker run failed. Different boundaries can report the same incident. |

**Fields and privacy.** `operationId` is the derived 64-character lowercase hex identifier shared by the reservation, receipt and outbox; omit malformed IDs. `operationType` and `status` use fixed domain labels. `errorCode` and `attempt` are numeric. HTTP records use the server `requestId`, handler method name (or `other`), `httpStatus` and `durationMs` until response commit, not stream completion. An HTTP 200 is not proof of a successful payment. Never log wallet IDs, idempotency keys, cards, amounts, balances, provider receipt IDs, headers, bodies, URLs, lease tokens, Kafka payloads or exception messages in wallet events. Keep linkable IDs out of metric tags and restrict access and retention at the collector.

Logs are for diagnosis, not a ledger or audit history. Use the existing Micrometer timers for latency and subscription counts; an operation result counter can include replays. Use persisted operations, receipts and the outbox for authoritative state. No external collector or monitoring backend is required by this configuration.

## Optional destinations

Stdout stays enabled. Append `log-file`, `log-elastic`, or both to the existing `SPRING_PROFILES_ACTIVE` list (for example, `local-top-up,log-file,log-elastic`). Neither optional destination is initialized by default. Both receive the same Logstash JSON and levels as stdout.

- **File:** `WALLET_LOG_FILE` selects the active file (default `logs/wallet.log`). Use a private directory writable by the wallet process; mount it if container logs must survive replacement. Files roll daily or at 20 MB and are compressed; archives retain up to seven days with a 200 MB total cap.
- **Elastic via Logstash:** Set `WALLET_LOG_ELASTIC_DESTINATION` to the collector's `host:port` (default `localhost:5044`). Configure a Logstash **TCP input** with TLS and the `json_lines` codec, not a Beats input or the Elasticsearch HTTP API. The collector certificate must match the hostname and chain to a JVM-trusted CA. Configure Elasticsearch credentials, index routing and retention at the collector, not in the wallet.

Both optional appenders use bounded asynchronous buffers and can drop records under load or during outages. Avoid collecting the same stream again from stdout or disk when TCP forwarding is active.

## Simulator and other WebFlux modules

Filter by `logger_name=simulator.events`. At INFO, `simulation.result` contains only fixed `operation` and `result` labels in addition to the standard log fields. Operations are `stripe.charge`, `paypal.create`, `paypal.capture`, and `redsys.authorize`. These describe fixture decisions, not financial transactions: a Redsys decline still returns HTTP 200, and a second PayPal capture reports `already_captured`, not `captured`. A delayed timeout cancelled before a response is produced does not emit `timeout_response`.

The simulator uses the same `http.response` (DEBUG) and `http.server_error` (ERROR) contract. Enable `LOGGING_LEVEL_SIMULATOR_EVENTS=DEBUG` for response diagnostics. Spring Boot also records standard `http.server.requests` metrics. The same sink profiles apply; their simulator settings are `SIMULATOR_LOG_FILE` (default `logs/simulator.log`) and `SIMULATOR_LOG_ELASTIC_DESTINATION` (default `localhost:5044`).

To opt another WebFlux executable in, supply a `StructuredEvents` bean with its service name and import `ObservabilityConfiguration` from `common`. In its own `logback-spring.xml`, bind `filePath` and `collector`, then include `logging/service-appenders.xml` from `common`; declare the managed Logstash appender at runtime for `log-elastic`. Domain event names and fields belong in the owning module.
