#!/bin/sh
# Uses only this disposable project's volumes. Requires Docker Compose, openssl, curl, python3.
set -eu
cd "$(dirname "$0")"
project="wallet-monitor-test-$(openssl rand -hex 5)"
export GRAFANA_ADMIN_PASSWORD="$(openssl rand -hex 24)" MONGO_EXPRESS_PASSWORD="$(openssl rand -hex 24)"
export WALLET_HTTP_PORT="${TEST_WALLET_PORT:-18090}" GRAFANA_HTTP_PORT="${TEST_GRAFANA_PORT:-13000}" MONGO_EXPRESS_HTTP_PORT="${TEST_MONGO_EXPRESS_PORT:-18081}"
stack() { docker compose -p "$project" -f compose.yaml -f compose.monitoring.yaml "$@"; }
cleanup() { stack down -v --remove-orphans; }
trap cleanup EXIT
stack up -d --build
wait_for() { i=0; until "$@"; do i=$((i+1)); [ "$i" -lt 90 ] || { echo "Timed out: $1" >&2; return 1; }; sleep 2; done; }
grafana="http://127.0.0.1:$GRAFANA_HTTP_PORT"
mongo="http://127.0.0.1:$MONGO_EXPRESS_HTTP_PORT"
wallet="http://127.0.0.1:$WALLET_HTTP_PORT"
query() { curl -fsS -u "admin:$GRAFANA_ADMIN_PASSWORD" -G --data-urlencode "query=$1" "$grafana/api/datasources/proxy/uid/local-prometheus/api/v1/query"; }
scraping() { data=$(query up) && printf '%s' "$data" | python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; sys.exit({x["metric"]["job"]:x["value"][1] for x in r} != {"wallet":"1","simulator":"1"})'; }
wait_for scraping
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$grafana/api/user")" = 401 ]
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$mongo/")" = 401 ]
curl -fsS -u "monitor:$MONGO_EXPRESS_PASSWORD" "$mongo/db/playtomic" >/dev/null
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$wallet/actuator/prometheus")" = 404 ]
curl -fsS "$wallet/api/v1/wallets/log" >/dev/null
requests() { data=$(query 'http_server_requests_seconds_count{job="wallet",method="GET",uri="/api/v1/wallets/log"}') && printf '%s' "$data" | python3 -c 'import json,sys; sys.exit(not any(float(x["value"][1]) >= 1 for x in json.load(sys.stdin)["data"]["result"]))'; }
wait_for requests
panels=$(curl -fsS -u "admin:$GRAFANA_ADMIN_PASSWORD" "$grafana/api/dashboards/uid/wallet-overview" | python3 -c '
import json, sys
d = json.load(sys.stdin)["dashboard"]
assert d["title"] == "Wallet overview" and len(d["panels"]) >= 4
for panel in d["panels"]:
    assert panel["datasource"]["uid"] == "local-prometheus"
    for target in panel["targets"]:
        print(target["expr"].replace("$__rate_interval", "5m"))')
printf '%s\n' "$panels" | while IFS= read -r expr; do
  query "$expr" >/dev/null || exit 1
done
echo 'Wallet dashboard provisioned, panel queries accepted, wallet traffic scraped, and UI access checked.'