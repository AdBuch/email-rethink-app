# Review: Branch `feature/phase-0-grundlagen`

**Datum:** 15.11.2026 · **Stand des Branches:** `73341f7` (40 Commits, 30 Dateien, ~800 Zeilen)
**Art:** statisches Code- und Dokumentations-Review. Builds konnten in der Review-Umgebung nicht ausgeführt werden (kein Zugriff auf crates.io/Maven), CI-Ergebnisse auf GitHub wurden nicht eingesehen.

## Gesamturteil

Solides, ehrlich dokumentiertes **Grundgerüst plus technischer Spike** – aber noch **keine lauffähige App**. `docs/PHASE-0-STATUS.md` sagt das selbst korrekt („Es wurden keine Builds oder Tests ausgeführt“). Die neuen ADRs (006–008) sind gut begründet und passen zu ADR-001/002. **Nicht als „Phase 0 fertig“ nach `main` mergen**, bevor die Blocker unten behoben und Builds grün sind.

**Positiv:**
- Klare Modulstruktur nach ADR-002; Rust-Kern + UniFFI (ADR-006) ist eine gute, begründete Wahl mit Fallback und Abnahmekriterien.
- Sicherheitshaltung stimmt: nur IMAPS, Zertifikats- und Hostnamenprüfung, keine Klartext-Fallbacks, keine Secrets in Logs.
- SQL-Upserts mit `ON CONFLICT DO UPDATE` statt `INSERT OR REPLACE` (verhindert Kaskadenlöschungen) – gut erkannt.
- Offene Punkte werden nachgeführt statt geraten.

## Befunde

### 🔴 Blocker (verhindern Build oder Kernfunktion)

| # | Ort | Befund | Empfehlung |
|---|---|---|---|
| B1 | Repo-Root | **Kein Gradle Wrapper** (`gradlew`, `gradle/wrapper/*`). Android-Build ist nicht reproduzierbar, CI kann ihn nicht ausführen. | Wrapper erzeugen und einchecken (ADR-008). |
| B2 | `iosApp/` | **Kein Xcode-Projekt**, nur zwei Swift-Dateien. iOS ist nicht baubar. | Xcode-Projekt (oder XcodeGen/Tuist-Spezifikation) anlegen, Rust-XCFramework + UniFFI-Swift-Bindings einbinden. |
| B3 | `rust/.../lib.rs` | `MailError` ist in der UDL ein `[Error]`-Enum, implementiert aber weder `Display` noch `std::error::Error`. UniFFI benötigt das für Fehlertypen → **Kompilierfehler wahrscheinlich**. | `thiserror` verwenden oder `Display` + `Error` implementieren. |
| B4 | `lib.rs` `fetch_inbox_async` | Abgefragt wird `BODY.PEEK[HEADER.FIELDS (FROM SUBJECT)]`, gelesen wird aber `message.header()`. Diese Methode liefert nur `BODY[HEADER]`/`RFC822.HEADER`, nicht `HEADER.FIELDS` → **Absender und Betreff bleiben vermutlich leer**. | `ENVELOPE` abfragen (liefert From, Subject, Date, Message-ID bereits geparst) plus `UID FLAGS INTERNALDATE`. Eigener Header-Parser entfällt. |
| B5 | `lib.rs` `validate` | Es wird **nur Port 993** akzeptiert; der GreenMail-Testaufbau läuft auf **3993** → die geplanten Integrationstests können den Code gar nicht erreichen. | Port-Policy: implizites TLS auf beliebigem Port erlauben; die Sicherheit kommt aus TLS, nicht aus der Portnummer. Port 143/STARTTLS separat und explizit. |
| B6 | `lib.rs` TLS | Nur `webpki-roots` als Vertrauensanker, keine Test-CA-Injektion → GreenMail (selbstsigniert) ist nicht testbar; auch Firmen-/Eigenserver mit eigener CA funktionieren nie. | `rustls-platform-verifier` (System-Trust-Store von Android/iOS) für Produktion; zusätzliche CA nur über eine explizite, testspezifische Konfiguration. |
| B7 | `rust/mail-transport` | **`Cargo.lock` fehlt** (von ADR-008 verlangt). | Einchecken. |

### 🟠 Wichtig (vor dem ersten echten Konto beheben)

| # | Ort | Befund | Empfehlung |
|---|---|---|---|
| W1 | `lib.rs` | Abruf über **Sequenznummern**, `UIDVALIDITY` wird nicht zurückgegeben – widerspricht ADR-006 („Mailbox + UIDVALIDITY + UID“). Das Schema hat `uid_validity`, der Transport liefert es nicht. | `UID FETCH` und `uid_validity` im `InboxSnapshot` zurückgeben. |
| W2 | `lib.rs` | Ein Fehler beim `LOGOUT` verwirft die bereits geladenen Daten (`ProtocolFailed`). | Logout-Fehler ignorieren bzw. nur protokollieren. |
| W3 | `lib.rs` | Synchroner Aufruf mit neuer Tokio-Runtime pro Aufruf, keine Verbindungswiederverwendung, kein Abbruch. Für den Spike ok, ADR-006 verlangt aber `suspend`/`async` mit Cancellation. | UniFFI-Async-Exporte nutzen; eine Runtime pro Prozess. |
| W4 | `Cargo.toml` | `tokio-rustls` 0.26 zieht standardmässig `aws-lc-rs` (C-Code, CMake) – erschwert Cross-Compile für iOS/Android. | Für mobile Targets auf `ring` umstellen (`default-features = false`, `features = ["ring", …]`) oder den aws-lc-Build in der CI nachweisen. |
| W5 | KMP ↔ Rust | Die KMP-Schnittstelle `MailTransport` (stateful `connect`, `Long`-UID, `from/date`) und der Rust-Vertrag (stateless `fetch_inbox`, `u32`-UID, `sender`) **passen nicht zusammen**; kein Adapter vorhanden. | Einen gemeinsamen Vertrag festlegen; Kotlin-Adapter auf die UniFFI-Bindings. |
| W6 | `Mail.sq` | SQLite-Fremdschlüssel (`ON DELETE CASCADE`) wirken nur mit `PRAGMA foreign_keys = ON`; die SQLDelight-Treiber setzen das nicht automatisch. Bei UIDVALIDITY-Wechsel fehlt eine Löschabfrage. | Pragma beim Öffnen setzen; Abfrage `deleteMessagesOfFolder`; deterministische Message-IDs (z. B. `folderId:uidValidity:uid`). |
| W7 | Datenbank | SQLCipher fehlt (ADR-007 verlangt es vor echten Mails). | Vor dem ersten echten Konto integrieren. |
| W8 | `AndroidManifest.xml` | Kein `allowBackup`/`dataExtractionRules` → Maildatenbank landet potenziell im Android-Backup. | Backup für DB und Schlüssel ausschliessen (ADR-007, Punkt Backup). |
| W9 | Android-Build | Keine `compileOptions`/`jvmToolchain` → Risiko „Inconsistent JVM-target compatibility“ zwischen Java und Kotlin. | `kotlin { jvmToolchain(17) }` in allen Modulen. |

### 🟡 Kleinere Punkte

- `parse_basic_headers`: keine Header-Entfaltung, kein RFC-2047-Decoding, nur `From:`/`from:` (nicht `FROM:`) – entfällt mit ENVELOPE (B4).
- Passwort als `String` ohne Nullsetzen nach Gebrauch (`zeroize`).
- `kotlin.mpp.androidSourceSetLayoutVersion=2` ist in Kotlin 2.x überflüssig.
- Plugin `org.jetbrains.compose` deklariert, aber nicht genutzt (UI ist nativ).
- GitHub-Actions nicht auf Commit-SHAs gepinnt (ADR-008 Punkt 7); nur Rust-CI, kein Android-Job.
- Benennung der ADR-Dateien uneinheitlich (`ADR-001-kein-server.md` vs. `ADR-006-RUST-IMAP-UNIFFI.md`).

### 📄 Dokumentation

- **ADR-009 weicht vom Konzept ab:** Dort ist Quipu „Beziehungen und Abzweigungen innerhalb eines Gesprächs“. Im beschlossenen Konzept ist Quipu dagegen **eine Schnur pro Kontakt bzw. Rechnungssteller, ein Knoten pro Mail/Rechnung** (Spez. 5.4 und 5.8, `konzepte/DARSTELLUNG-PRO-TYP.md`). ADR-009 korrigieren.
- `SPEZIFIKATION.md` 5.8 verweist noch nicht auf ADR-009; ADR-006 bis 008 fehlen in der Liste „Verwandte Dokumente“.
- Die Rechnungsansicht (Kartenstapel, ✓ Bezahlt, ⇪ Teilen, Quipu-Leiste) und die Darstellung pro Mail-Typ sind im Branch noch nicht berücksichtigt – erwartbar, da Phase 1.

## Empfohlene Reihenfolge

1. B3, B4, B5, B7 beheben → `cargo test` + Clippy grün.
2. B6 + GreenMail-Integrationstest mit Test-CA (inkl. Negativtests aus ADR-006).
3. B1 + W9 → Android-Debug-Build in der CI.
4. B2 → iOS-Build auf macOS-Runner.
5. W1, W5, W6 → echter Datenfluss Rust → KMP-Adapter → SQLDelight → Inbox-UI.
6. W7, W8 → erst danach echte Konten.
