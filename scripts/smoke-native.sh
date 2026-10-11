#!/usr/bin/env bash
# Exercise HTTP/auth/Native JSON reflection and state recovery without Docker.
set -euo pipefail
binary=$(realpath "${1:?binary required}")
root=$(mktemp -d)
pid=''
cleanup() {
  if [[ -n $pid ]]; then kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; fi
  rm -rf "$root"
}
trap cleanup EXIT
mkdir "$root/state"
printf 'services: {}\n' | tee "$root/docker-compose.yml" > "$root/docker-compose.updater.yml"
printf 'ZRLOG_IMAGE=zrlog/zrlog:local\n' > "$root/.env"
cat > "$root/state/state.json" <<'JSON'
{"schemaVersion":1,"settings":{"repository":"","channel":"release"},"jobs":[{"id":"native-smoke-task-1234","phase":"replacing","version":"4.0.0","buildId":"1234567"}]}
JSON
export UPDATER_TOKEN=native_smoke_token_1234567890_abcdef
export UPDATER_PROJECT=native-smoke UPDATER_DEPLOYMENT_DIR="$root" UPDATER_STATE_DIR="$root/state"
export UPDATER_BIND=127.0.0.1
# A collision fails closed; the authenticated state check prevents testing another server.
UPDATER_PORT=$(shuf -i 20000-60000 -n 1)
export UPDATER_PORT
url="http://127.0.0.1:$UPDATER_PORT"
for _attempt in 1 2; do
  "$binary" > "$root/server.log" 2>&1 &
  pid=$!
  ready=false
  for ((i=0; i<100; i++)); do
    if ! kill -0 "$pid" 2>/dev/null; then cat "$root/server.log" >&2; exit 1; fi
    if curl --noproxy '*' -fsS --max-time 1 "$url/health" > "$root/health.json" 2>/dev/null; then ready=true; break; fi
    sleep 0.2
  done
  [[ $ready == true ]] || { cat "$root/server.log" >&2; exit 1; }
  jq -e '. == {status:"ok"}' "$root/health.json" >/dev/null
  "$binary" --healthcheck
  [[ $(curl --noproxy '*' -sS --max-time 3 -o /dev/null -w '%{http_code}' "$url/v1/upgrades/native-smoke-task-1234") == 401 ]]
  curl --noproxy '*' -fsS --max-time 3 -D "$root/headers" \
    -H "Authorization: Bearer $UPDATER_TOKEN" "$url/v1/upgrades/native-smoke-task-1234" > "$root/job.json"
  jq -e '.id == "native-smoke-task-1234" and .phase == "interrupted" and .recoveryRequired == true' "$root/job.json" >/dev/null
  tr -d '\r' < "$root/headers" | grep -qi '^Cache-control: no-store$'
  kill "$pid"
  status=0
  wait "$pid" || status=$?
  pid=''
  [[ $status == 0 || $status == 143 ]]
done
printf 'Native HTTP/auth/JSON/restart smoke passed; binary %s bytes\n' "$(stat -c %s "$binary")"
