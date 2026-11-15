# Email-Rethink-App

Native E-Mail-App für iPhone und Android, die das Postfach automatisch abarbeitet: Newsletter sammeln und entsorgen, Rechnungen erkennen und nach Rechnungssteller archivieren, Tickets ins Wallet, Codes zum Kopieren – und nur das Wichtige auf die **Pole Position**. Mit KI-Antwortassistent, neuer Verlaufsdarstellung und automatischer Verschlüsselung (OpenPGP/Autocrypt, S/MIME). Ohne eigenen Server.

## Dokumentation

- [Spezifikation](docs/SPEZIFIKATION.md)
- [Offene Punkte](docs/OFFENE-PUNKTE.md)
- [Architekturentscheidungen (ADR)](docs/adr/)
- [Standards-Referenz](docs/STANDARDS.md)
- [Brainstorming Verlaufsdarstellung](docs/konzepte/VERLAUF-BRAINSTORM.md)

## Eckdaten

- Kotlin Multiplatform + SwiftUI + Jetpack Compose
- IMAP/SMTP, Gmail API, Microsoft Graph, JMAP
- On-Device-KI, Cloud-KI nur per Opt-in
- Kein Hersteller-Server
