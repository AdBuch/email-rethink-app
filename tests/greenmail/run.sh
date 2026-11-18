#!/usr/bin/env bash
# Startet GreenMail mit frischen Testzertifikaten, führt die IMAP-Integrationstests
# aus und räumt wieder auf. Voraussetzungen: Docker, OpenSSL, Rust.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
COMPOSE=(docker compose -f "$HERE/compose.yaml")

"$HERE/gen-certs.sh" "$HERE/certs"

cleanup() { "${COMPOSE[@]}" down --volumes >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup
"${COMPOSE[@]}" up -d

wait_for_port() {
  local port="$1"
  for _ in $(seq 1 60); do
    if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then return 0; fi
    sleep 1
  done
  echo "Port $port wurde nicht erreichbar." >&2
  "${COMPOSE[@]}" logs >&2
  return 1
}
wait_for_port 3993
wait_for_port 3143
wait_for_port 4993

# Beleg, dass der Testserver wirklich das erzeugte Zertifikat ausliefert.
echo | openssl s_client -connect 127.0.0.1:3993 -servername localhost \
  -CAfile "$HERE/certs/ca.pem" -verify_return_error 2>/dev/null \
  | grep -E "Verify return code|subject=" || true

status=0
(
  cd "$ROOT/rust/mail-transport"
  GREENMAIL_CA_PEM="$HERE/certs/ca.pem" \
    cargo test --locked --features test-ca --test greenmail -- --nocapture
) || status=$?

if [ "$status" -ne 0 ]; then
  echo "--- GreenMail-Protokoll ---" >&2
  "${COMPOSE[@]}" logs --tail 80 >&2 || true
fi
exit "$status"
