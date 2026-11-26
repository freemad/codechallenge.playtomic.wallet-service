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
| ERROR | `audit.balance_discrepancy`, `audit.run_failed`, `audit.alert_delivery_incomplete` | A mismatch committed, an audit attempt failed, or the alert relay could not finish. Financial evidence and alert delivery are handled by the [balance auditor](wallet-balance-audit.md), not by these logs. |

**Fields and privacy.** `operationId` is the derived 64-character lowercase hex identifier shared by the reservation, receipt and outbox; omit malformed IDs. `operationType` and `status` use fixed domain labels. `errorCode` and `attempt` are numeric. HTTP records use the server `requestId`, handler method name (or `other`), `httpStatus` and `durationMs` until response commit, not stream completion. An HTTP 200 is not proof of a successful payment. Never log wallet IDs, idempotency keys, cards, amounts, balances, provider receipt IDs, headers, bodies, URLs, lease tokens, Kafka payloads or exception messages in wallet events. Keep linkable IDs out of metric tags and restrict access and retention at the collector.

Logs are for diagnosis, not a ledger or audit history. Use the existing Micrometer timers for latency and subscription counts; an operation result counter can include replays. Use persisted operations, receipts and the outbox for authoritative state. No external collector or monitoring backend is required by this configuration.

## Optional destinations

Stdout stays enabled. Append `log-file`, `log-elastic`, or both to the existing `SPRING_PROFILES_ACTIVE` list (for example, `local-top-up,log-file,log-elastic`). Neither optional destination is initialized by default. Both receive the same Logstash JSON and levels as stdout.

- **File:** `WALLET_LOG_FILE` selects the active file (default `logs/wallet.log`). Use a private directory writable by the wallet process; mount it if container logs must survive replacement. Files roll daily or at 20 MB and are compressed; archives retain up to seven days with a 200 MB total cap.
- **Elastic forwarding:** Set `WALLET_LOG_ELASTIC_DESTINATION` to the collector's `host:port` (default `localhost:5044`). The receiver must accept TLS TCP JSON lines, not Beats or Elasticsearch HTTP. The collector certificate must match the hostname and chain to a JVM-trusted CA.

Both optional appenders use bounded asynchronous buffers and can drop records under load or during outages. Avoid collecting the same stream again from stdout or disk when TCP forwarding is active.

## Local Kafka Connect → Elasticsearch with Compose

These commands require the optional monitoring checkout, not the base stack. See [local monitoring availability](local-monitoring.md) first.

Wallet and simulator send TLS JSON lines to Vector. It filters for `wallet.events` and `simulator.events` and allowlists fields **before** producing to `service.logs.v1`: framework logs, raw messages and request/operation IDs are not forwarded. Kafka Connect `8.1.0` consumes that topic and writes to the `service.logs.v1` index on Elasticsearch `8.19.26`. The worker image adds Confluent’s Elasticsearch sink `15.1.5` (Confluent Community License) at build time. No Schema Registry or Spring Elasticsearch client is needed. Actuator remains in the services; service metrics are not exported to Elasticsearch.

From the repository root, set two private passwords once per stack; keep them for subsequent starts with the same volumes:

```sh
export ELASTIC_PASSWORD=$(openssl rand -hex 24) COLLECTOR_KEYSTORE_PASSWORD=$(openssl rand -hex 24)
stack() { docker compose -f deployment/compose.yaml -f deployment/compose.elastic.yaml "$@"; }
export LOGGING_LEVEL_WALLET_EVENTS=DEBUG # optional: include successful HTTP responses
stack up -d --build
stack exec connect curl -fsS localhost:8083/connectors/service-logs/status
curl -fsS http://127.0.0.1:8090/api/v1/wallets/log
stack exec elasticsearch sh -c 'curl -fsS -u "elastic:$ELASTIC_PASSWORD" "http://localhost:9200/service.logs.v1/_search?q=event:http.response"'
stack exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group connect-service-logs
stack down
```

Check **both** connector and task state (`RUNNING`) and watch consumer lag. If Elasticsearch stops, Kafka retains accepted records (seven days by default). After fixing a prolonged outage, restart failed tasks with `stack exec connect curl -fsS -X POST 'localhost:8083/connectors/service-logs/restart?includeTasks=true&onlyFailed=true'`. Connect resumes from committed offsets; document IDs derive from topic, partition and offset so retries of a Kafka record do not create extra documents. Events can still be lost before broker acknowledgment. Neither the wallet outbox nor `wallet.audit.alerts.v1` is enabled or consumed by this stack.

This is a **single-broker local demo**. Kafka, Connect REST and authenticated Elasticsearch HTTP are not host-published; backend traffic is plaintext and the sink uses the `elastic` account. Production needs replication, TLS/authentication, scoped credentials, certificate rotation, access controls, index retention and disk/lag alerts. `stack down` retains keys, logs and offsets; do not use `down -v` on a stack with wallet data you need. `sh deployment/test-elastic.sh` uses a disposable project: it reads a real wallet event from Kafka while Elasticsearch is stopped, then checks for the same document after restart.

## Simulator and other WebFlux modules

Filter by `logger_name=simulator.events`. At INFO, `simulation.result` contains only fixed `operation` and `result` labels in addition to the standard log fields. Operations are `stripe.charge`, `paypal.create`, `paypal.capture`, and `redsys.authorize`. These describe fixture decisions, not financial transactions: a Redsys decline still returns HTTP 200, and a second PayPal capture reports `already_captured`, not `captured`. A delayed timeout cancelled before a response is produced does not emit `timeout_response`.

The simulator uses the same `http.response` (DEBUG) and `http.server_error` (ERROR) contract. Enable `LOGGING_LEVEL_SIMULATOR_EVENTS=DEBUG` for response diagnostics. Spring Boot also records standard `http.server.requests` metrics. The same sink profiles apply; their simulator settings are `SIMULATOR_LOG_FILE` (default `logs/simulator.log`) and `SIMULATOR_LOG_ELASTIC_DESTINATION` (default `localhost:5044`).

To opt another WebFlux executable in, supply a `StructuredEvents` bean with its service name and import `ObservabilityConfiguration` from `common`. In its own `logback-spring.xml`, bind `filePath` and `collector`, then include `logging/service-appenders.xml` from `common`; declare the managed Logstash appender at runtime for `log-elastic`. Domain event names and fields belong in the owning module.