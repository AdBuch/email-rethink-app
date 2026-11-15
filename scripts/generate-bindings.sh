#!/usr/bin/env bash
# Erzeugt die UniFFI-Bindings aus dem Rust-Kern.
#   scripts/generate-bindings.sh kotlin <zielverzeichnis>
#   scripts/generate-bindings.sh swift  <zielverzeichnis>
set -euo pipefail
LANGUAGE="$1"
OUT="$2"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/rust/mail-transport"

case "$(uname -s)" in
  Darwin) HOST_LIB="target/debug/libmail_transport.dylib" ;;
  *) HOST_LIB="target/debug/libmail_transport.so" ;;
esac

# Die Host-Bibliothek liefert die Metadaten für den Library-Modus von uniffi-bindgen.
cargo build --locked --lib
mkdir -p "$OUT"
cargo run --locked --features bindgen --bin uniffi-bindgen -- \
  generate --library "$HOST_LIB" --language "$LANGUAGE" --out-dir "$OUT" --no-format
