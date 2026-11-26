# Local monitoring availability

The base Compose stack in this checkout runs the wallet, simulator and MongoDB. It does not install Grafana, Prometheus, Mongo Express, or an Elastic ingestion pipeline. Use `docker compose -f deployment/compose.yaml logs wallet simulator` for local JSON logs and `curl -fsS http://127.0.0.1:8090/actuator/health/readiness` for wallet readiness.

The optional deployment is maintained on the separate `infra/wallet/local-observability` branch. In a clone containing that ref, read its matching instructions without switching the current checkout:

```sh
git --no-pager show infra/wallet/local-observability:docs/local-monitoring.md
git --no-pager show infra/wallet/local-observability:docs/wallet-logging.md
```

Use the overlays, scripts and matching base Compose from that revision together in a separate checkout. The Elastic Compose commands in the [logging guide](wallet-logging.md) require those artifacts. Do not expose local top-up writes through a monitoring proxy.
