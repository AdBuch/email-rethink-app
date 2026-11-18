# Rust-Mail-Transport (Entwicklungszyklus 1)

Dieser Crate ist das **erste überprüfbare UniFFI-Grundgerüst** gemäss ADR-006. Er stellt derzeit nur eine Versionsabfrage und eine restriktive IMAPS-Port-Prüfung bereit. **Er ist noch kein funktionsfähiger IMAP-Client.**

## Lokal prüfen

```sh
cd rust/mail-transport
cargo test
cargo fmt --check
cargo clippy --all-targets -- -D warnings
```

Die generierte Kotlin-/Swift-Anbindung und die mobilen Bibliotheken werden erst im nächsten Integrationsschritt paketiert. Danach folgt ein echter IMAP/TLS-Client mit GreenMail-Integrationstests.

Keine echten Mailzugangsdaten in Testdateien oder Logs verwenden.
