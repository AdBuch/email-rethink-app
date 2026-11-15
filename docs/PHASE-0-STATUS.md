# Phase 0: Implementierungsstand

Stand: 15.11.2026. Branch: `feature/phase-0-grundlagen`.

## Angelegt
- Gradle-Multiprojekt und KMP-Modulstruktur nach ADR-002.
- SQLDelight-Schema für Konten, Ordner und Nachrichten mit UID-basierter Eindeutigkeit.
- Plattformunabhängige Schnittstelle `MailTransport`.
- Android-Inbox als Jetpack-Compose-Ansicht (noch ohne Datenanbindung).
- iOS-Inbox als SwiftUI-Ansicht (noch ohne Datenanbindung).

## Nicht implementiert oder nicht verifiziert
- IMAP-TLS-Client, Authentifizierung und Header-Fetch.
- GreenMail-Integrationstests und Docker-Testumgebung.
- SQLCipher-Verschlüsselung und sichere Schlüsselablage.
- DB-Treiberintegration in die Apps und echter Inbox-Datenfluss.
- SMTP, OAuth/PKCE und Provider-Autokonfiguration.
- iOS-Xcode-Projekt und Framework-Integration.
- Gradle Wrapper, Android-/iOS-Builds und Tests.

**Wichtig:** Die aktuellen UI-Ansichten sind nur leere Hüllen, keine funktionsfähigen Mailclients. Es wurden keine Builds oder Tests ausgeführt.

## Nächste technische Schritte
1. KMP-/Gradle-/Xcode-Toolchain reproduzierbar einrichten und Builds prüfen.
2. IMAP-Adapter über eine auf Android und iOS verfügbare, TLS-validierende Implementierung evaluieren; GreenMail-Tests ergänzen.
3. SQLCipher und sichere Schlüsselspeicherung integrieren, bevor reale Konten gespeichert werden.
4. Datenbank und Inbox verbinden, dann SMTP und OAuth ergänzen.
