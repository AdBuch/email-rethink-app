# ADR-006: Gemeinsamer Rust-IMAP-Kern mit UniFFI

- **Status:** Angenommen (Architektur); konkrete IMAP-Crate bis zum technischen Spike vorläufig
- **Datum:** 2026-11-15
- **Entscheidung:** Offener Punkt 14
- **Bezug:** ADR-001 (kein Hersteller-Backend), ADR-002 (KMP und native Oberflächen), ADR-003 (Mail-Protokolle), `docs/STANDARDS.md`

## Kontext

Phase 0 benötigt eine sichere, auf Android und iOS funktionierende IMAP-Anbindung für Kontoverbindung, Ordnerliste und Header-Synchronisation. Zwei getrennte native IMAP-Clients würden Protokollverhalten, Fehlerbehandlung und Sicherheitskorrekturen duplizieren. Die bestehende Kotlin-Multiplatform-Architektur und die nativen SwiftUI-/Compose-Oberflächen bleiben bestehen.

## Entscheidung

1. Ein **gemeinsamer Rust-Kern** implementiert IMAP-Verbindungsverwaltung, TLS, Authentifizierung, Ordnerliste, UID-basierte Header-Abfrage, Abbruch und Fehlerabbildung. Kein selbstgeschriebener IMAP-Parser.
2. **Mozilla UniFFI** generiert Kotlin- und Swift-Bindings. Android bindet Rust über JNI/UniFFI ein; iOS verwendet ein XCFramework mit generierten Swift-Bindings. Die bestehende KMP-Schnittstelle `MailTransport` bleibt die fachliche Grenze; ein Adapter delegiert an den Rust-Kern. SwiftUI ruft denselben Kern über Swift-Bindings bzw. eine dünne iOS-Fassade auf. Keine parallele IMAP-Implementierung in Kotlin und Swift.
3. **Bevorzugter Bibliothekskandidat für den Spike:** `async-imap` 0.12.x mit `runtime-tokio`, `tokio` und `rustls`/`tokio-rustls`. Die Tokio-Feature-Unterstützung ist vorhanden; die konkrete TLS-Stream-Integration und mobile Kompilierung müssen nachgewiesen werden. Versionen nach erfolgreichem Spike exakt pinnen.
4. **Alternative bei fehlgeschlagenem Spike:** `io-imap` (Pimalaya), dessen I/O-freier Ansatz und rustls-Unterstützung die Netzwerkschicht explizit kontrollierbar machen. Ein Wechsel erfordert einen dokumentierten Vergleich und ggf. eine Ergänzung dieses ADR, nicht eine stille Abweichung.
5. Der Rust-Kern ist eine **lokale Clientbibliothek**, kein Dienst, Proxy oder Hersteller-Backend. Netzwerkverbindungen gehen direkt zum Mailserver.

## Evaluation konkreter Bibliotheken

| Bibliothek | Vorteile | Risiken / Bewertung |
|---|---|---|
| `async-imap` 0.12.x | Asynchron; LIST, FETCH, SEARCH, Authentifizierung; Tokio-Feature | TLS-Stream selbst integrieren; IMAP4rev2 und Erweiterungen nicht pauschal garantiert. **Primärkandidat.** |
| `io-imap` 0.8.x | I/O-freier Kern, rustls, dokumentierte RFC-Module und XOAUTH2 | API-/Integrationsaufwand und mobile Builds noch unbewiesen. **Fallback/Benchmark.** |
| `imap` 2.4.x | Etablierte synchrone API | Blockierendes Modell; veröffentlichte 2.4.1-Version älter. **Nicht bevorzugt.** |
| `daaki-imap` 0.2.x | Tokio/rustls und IMAP4rev1/rev2 | Noch junge Bibliothek; Reife und Erweiterungsumfang zu prüfen. **Beobachten.** |
| Mozilla `uniffi-rs` | Generiert Kotlin- und Swift-Bindings, unterstützt asynchrone Exporte | Toolchain, Binärgrössen und Cancellation-Semantik auf beiden Plattformen testen. **Binding-Wahl.** |

Diese Bewertung beruht auf öffentlich dokumentierten APIs und Paketmetadaten; sie ersetzt **keinen** erfolgreichen Build, Security-Review oder Integrationstest.

## Sicherheits- und Protokollregeln

- TLS zwingend: IMAPS über Port 993; STARTTLS auf 143 nur mit expliziter Policy und erfolgreichem Upgrade **vor** Authentifizierung. Kein stiller Rückfall auf Klartext.
- Zertifikatskette und Hostname prüfen; keine unsicheren Verifier, keine pauschale Zertifikatsausnahme. Systemvertrauensanker bzw. definierter Trust-Store; Fehler explizit anzeigen.
- Authentifizierung über OAuth2/XOAUTH2, wo der Anbieter dies verlangt, und Passwort/App-Passwort für kompatible IMAP-Server. Secrets niemals loggen; Speicherung nur über Plattform-Keychain/Keystore nach gesondertem Sicherheitsentscheid.
- Synchronisation nach **Mailbox + UIDVALIDITY + UID**, nicht nach Sequenznummer; UIDVALIDITY-Wechsel und gelöschte Nachrichten behandeln. Header-Fetch ohne unnötigen Body-Download.
- IMAP4rev1 (RFC 3501) als Mindestbasis, IMAP4rev2 (RFC 9051) und Erweiterungen nur bei nachgewiesener Unterstützung. CAPABILITY auswerten. RFC 8314 für TLS-Policy berücksichtigen.
- Begrenzte Timeouts, maximale Header-/Antwortgrössen, kontrollierte Parallelität und klare Fehlerklassen (Netzwerk, TLS, Auth, Protokoll, Rate Limit, Abbruch).
- Kein Versand und keine Nachrichtendaten in Telemetrie oder Logs; keine echten Konten in Tests.

## Geplante Struktur

```text
rust/mail-transport/          # Cargo crate: IMAP + TLS + UniFFI exports
shared/core-transport/        # KMP MailTransport + Android Rust-Adapter
androidApp/                   # Compose, Rust .so + generierte Kotlin-Bindings
iosApp/                       # SwiftUI, XCFramework + generierte Swift-Bindings
tests/greenmail/              # Lokaler Testserver und Integrationsszenarien
```

## Abnahmekriterien für den technischen Spike

1. Reproduzierbarer Rust-Build für Android arm64 und iOS Gerät/Simulator, generierte UniFFI-Bindings; Android-App und iOS-App können die Bibliothek laden.
2. GreenMail per Docker: IMAPS mit vertrauenswürdigem Testzertifikat; Verbindung, LOGIN, LIST, SELECT, UID FETCH nur Header, Logout und Wiederverbindung.
3. Negativtests: falscher Hostname, ungültiges Zertifikat, abgelaufenes Zertifikat, falsche Zugangsdaten, Timeout, Verbindungsabbruch, UIDVALIDITY-Wechsel; niemals Klartext-Fallback.
4. XOAUTH2 gegen einen kontrollierten Testserver oder Test-Doppel; reale Provider-Integration als gesonderter Test ohne Secrets im Repository.
5. Kotlin-`suspend`- und Swift-`async`-Aufruf inklusive Fehlerübersetzung, Cancellation, Speicherfreigabe und Hintergrund-/Vordergrundwechsel prüfen.
6. CI führt Rust-Unit- und GreenMail-Integrationstests auf Linux sowie Android-Build aus; iOS-Build auf macOS-Runner.
7. Rust-Crates und Lizenzen prüfen; Cargo.lock einchecken; Sicherheits- und Dependency-Scans etablieren.

**Freigaberegel:** Die Wahl von `async-imap` wird erst nach Erfüllung dieser Kriterien endgültig. Bei Scheitern wird `io-imap` anhand derselben Kriterien geprüft.

## Konsequenzen

**Vorteile:** Ein IMAP-Protokollkern für beide Apps; konsistentes Verhalten und Sicherheitsupdates; KMP-/SwiftUI-/Compose-Architektur bleibt erhalten.

**Nachteile:** Zusätzliche Rust-/Cargo-/UniFFI-Toolchain, FFI-Komplexität, XCFramework-/JNI-Paketierung, grösserer CI- und Debugging-Aufwand. Der Rust-Kern wird bewusst auf den Transport begrenzt; SQLDelight, Klassifizierung und UI verbleiben in ihren bereits beschlossenen Modulen.

## Quellen

- https://docs.rs/async-imap/0.12.0/async_imap/
- https://docs.rs/io-imap/latest/io_imap/
- https://docs.rs/imap/latest/imap/
- https://docs.rs/daaki-imap/latest/daaki_imap/
- https://github.com/mozilla/uniffi-rs
- https://mozilla.github.io/uniffi-rs/latest/futures.html
