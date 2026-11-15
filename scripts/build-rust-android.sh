#!/usr/bin/env bash
# Baut den Rust-Kern für Android (arm64-v8a für Geräte, x86_64 für Emulatoren).
# Voraussetzungen: Rust-Targets aarch64-linux-android und x86_64-linux-android,
# cargo-ndk, Android NDK (ANDROID_NDK_HOME).
#   scripts/build-rust-android.sh <jniLibs-zielverzeichnis>
set -euo pipefail
OUT="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/rust/mail-transport"
mkdir -p "$OUT"
cargo ndk -t arm64-v8a -t x86_64 -o "$OUT" build --release --locked
