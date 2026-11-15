# Phase 0: Implementierungsstand

Stand: 15.11.2026. Branch: `feature/phase-0-grundlagen`.

## Entwicklungszyklus 1 (15.11.2026)
- Rust-Crate `rust/mail-transport` mit UniFFI-UDL, Build-Skript, Versionsfunktion und restriktiver IMAPS-Port-Policy angelegt.
- Ersten IMAPS-Netzwerkpfad mit `async-imap`, Tokio und `rustls` implementiert: TLS, Login, LIST, EXAMINE, begrenzter Header-FETCH, Logout. **Noch nicht kompiliert oder getestet.**
- GreenMail-Docker-Compose als Vorbereitung angelegt; noch keine laufenden Integrationstests.
- Rust-Unit-Tests und GitHub-Actions-Workflow (`rust-check`) ergänzt; Ausführungsergebnisse noch nicht verifiziert.
- SQLDelight-Upserts von `INSERT OR REPLACE` auf `ON CONFLICT DO UPDATE` umgestellt, um unbeabsichtigte Kaskadenlöschungen zu vermeiden.
- Architekturentscheidungen ADR-006 bis ADR-009 festgehalten.

## Angelegt
- Gradle-Multiprojekt und KMP-Modulstruktur nach ADR-002.
- SQLDelight-Schema für Konten, Ordner und Nachrichten mit UID-basierter Eindeutigkeit.
- Plattformunabhängige Schnittstelle `MailTransport`.
- Android-Inbox als Jetpack-Compose-Ansicht (noch ohne Datenanbindung).
- iOS-Inbox als SwiftUI-Ansicht (noch ohne Datenanbindung).

## Nicht implementiert oder nicht verifiziert
- IMAP-TLS-Client-Code vorhanden, aber **nicht kompiliert/verifiziert**; XOAUTH2, Zertifikats-Test-CA, robuste MIME-Dekodierung, UIDVALIDITY-/inkrementelle Synchronisation fehlen.
- GreenMail-Integrationstests fehlen; Docker-Compose-Konfiguration ist vorhanden, aber noch nicht verifiziert.
- SQLCipher-Verschlüsselung und sichere Schlüsselablage.
- DB-Treiberintegration in die Apps und echter Inbox-Datenfluss.
- SMTP, OAuth/PKCE und Provider-Autokonfiguration.
- iOS-Xcode-Projekt und Framework-Integration.
- Gradle Wrapper, Android-/iOS-Builds und ausgeführte Tests (Rust-CI ist konfiguriert, aber noch nicht verifiziert).

**Wichtig:** Die aktuellen UI-Ansichten sind nur leere Hüllen, keine funktionsfähigen Mailclients. Es wurden keine Builds oder Tests ausgeführt.

## Nächste technische Schritte
1. KMP-/Gradle-/Xcode-Toolchain reproduzierbar einrichten und Builds prüfen.
2. IMAP-Adapter über eine auf Android und iOS verfügbare, TLS-validierende Implementierung evaluieren; GreenMail-Tests ergänzen.
3. SQLCipher und sichere Schlüsselspeicherung integrieren, bevor reale Konten gespeichert werden.
4. Datenbank und Inbox verbinden, dann SMTP und OAuth ergänzen.
