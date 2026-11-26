#!/bin/sh
# Disposable local smoke test. Requires Docker Compose, openssl, curl and python3.
set -eu
cd "$(dirname "$0")"
project="wallet-elastic-test-$(openssl rand -hex 5)"
export ELASTIC_PASSWORD="$(openssl rand -hex 24)" COLLECTOR_KEYSTORE_PASSWORD="$(openssl rand -hex 24)"
export WALLET_HTTP_PORT="${TEST_WALLET_PORT:-18090}" LOGGING_LEVEL_WALLET_EVENTS=DEBUG
stack() { docker compose -p "$project" -f compose.yaml -f compose.elastic.yaml "$@"; }
cleanup() { stack down -v --remove-orphans; }
trap cleanup EXIT
wait_for() { n=0; until "$@"; do n=$((n+1)); [ "$n" -lt 120 ] || return 1; sleep 2; done; }
wallet() { curl -fsS "http://127.0.0.1:$WALLET_HTTP_PORT/api/v1/wallets/log" >/dev/null; }
es() { stack exec -T elasticsearch sh -ec 'curl -fsS -u "elastic:$ELASTIC_PASSWORD" "http://localhost:9200/$1"' sh "$1"; }
ready() { wallet && es 'service.logs.v1/_count?q=event:http.response' | grep -Eq '"count":[1-9]'; }
stack up --build -d
wait_for ready
stack stop elasticsearch
offset=$(stack exec -T kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:9092 --topic service.logs.v1 | cut -d: -f3)
wallet
record=$(stack exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic service.logs.v1 --partition 0 --offset "$offset" --max-messages 1 --timeout-ms 30000)
# Reject raw appender fields before checking sink recovery.
RECORD="$record" python3 -c 'import json,os; r=json.loads(os.environ["RECORD"]); assert r["logger_name"]=="wallet.events" and r["event"]=="http.response" and "requestId" not in r and "message" not in r'
stack start elasticsearch
# With key.ignore=true Connect derives the document ID from the Kafka coordinates.
indexed() {
  response=$(es "service.logs.v1/_doc/service.logs.v1+0+$offset" 2>/dev/null) || return 1
  printf '%s' "$response" | RECORD="$record" python3 -c 'import json,os,sys; sys.exit(json.load(sys.stdin)["_source"] != json.loads(os.environ["RECORD"]))'
}
wait_for indexed
echo 'Wallet event confirmed in Kafka during Elasticsearch outage and indexed after restart.'