# Local Compose monitoring

The monitoring overlay uses the locally tagged images `prom/prometheus:v3.14.0`, `grafana/grafana:13.0.7`, and `mongo-express:1.0.2-20-alpine3.19` without pulling replacements. It does not require Kafka or Elasticsearch. From the repository root:

```sh
export GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 24)
export MONGO_EXPRESS_PASSWORD=$(openssl rand -hex 24)
stack() { docker compose -f deployment/compose.yaml -f deployment/compose.monitoring.yaml "$@"; }
stack up -d --build
```

- **Grafana:** Open <http://127.0.0.1:3000>, sign in as `admin` with `GRAFANA_ADMIN_PASSWORD`, and select **Explore → Prometheus**. Query `up{job=~"wallet|simulator"}`: after startup both targets should be `1`. Generate a request with `curl -fsS http://127.0.0.1:8090/api/v1/wallets/log`, then query `http_server_requests_seconds_count{job="wallet",uri="/api/v1/wallets/log"}` after the next scrape. The provisioned data source uses Compose DNS, not localhost. The Grafana admin password is applied on first initialization; keep it with its volume or reset it in Grafana.
- **Prometheus:** Scrapes each service’s Spring Boot Actuator `/actuator/prometheus` endpoint on internal port `9091` every 15 seconds. Neither the scrape port nor the Prometheus UI is published. Use Grafana to explore JVM, HTTP and application metrics. A successful scrape is not a wallet balance audit or a payment outcome.
- **Mongo Express:** Open <http://127.0.0.1:8081>, sign in as `monitor` with `MONGO_EXPRESS_PASSWORD`, and inspect the `playtomic` database on `wallet-rs`. This is a database browser, not a MongoDB metrics exporter. The UI hides write controls; the base replica set does **not** enforce MongoDB authorization. Use only trusted local demo data. Do not expose or proxy this UI; for real data, require TLS and a database-enforced read-only account.

Override the loopback ports with `GRAFANA_HTTP_PORT`, `MONGO_EXPRESS_HTTP_PORT`, or `WALLET_HTTP_PORT` if necessary. To combine metrics with the [log pipeline](wallet-logging.md), also pass `-f deployment/compose.elastic.yaml` and supply that overlay’s passwords. `stack down` stops the stack but retains named volumes, including Grafana state, Prometheus history and wallet data. Do not use `down -v` on a project with data you need.

Run `sh deployment/test-monitoring.sh` for a disposable end-to-end check with separate ports and volumes. It queries both scrape targets through Grafana, checks an actual wallet HTTP meter, and verifies UI access controls.

To change scrape targets or timing, set `PROMETHEUS_CONFIG_FILE` to a complete Prometheus YAML file (default: `./monitoring/prometheus.yaml`, relative to `deployment/`). Set `GRAFANA_PROMETHEUS_INTERVAL` to the same scrape interval (default: `15s`); it does not change Prometheus itself. `GRAFANA_PROMETHEUS_URL` defaults to `http://prometheus:9090`, and `GRAFANA_DASHBOARD_FOLDER` defaults to `Wallet`.

## Wallet charts

Open [Wallet overview](http://127.0.0.1:3000/d/wallet-overview/wallet-overview) or choose **Dashboards → Wallet → Wallet overview**. The dashboard is provisioned automatically and refreshes every 15 seconds. It shows scrape health, API requests per second by route and HTTP status, mean request latency by route, and emitted mutation results by operation and business status.

To see traffic without performing a payment, call `GET /api/v1/wallets/log` several times across two or more scrapes. Latency can be blank when there are no requests. Mutation series appear only after a top-up or internal spend emits a result, and include replays; they do not count unique payments.