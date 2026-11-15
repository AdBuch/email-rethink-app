# ADR-002: Kotlin Multiplatform mit nativer UI

**Status:** Angenommen · 15.11.2026

## Kontext

Die App soll auf iPhone und Android nativ wirken. Ein Grossteil der Logik (Protokolle, Synchronisation, Klassifizierung, Extraktion, Krypto) ist plattformunabhängig.

## Entscheidung

- **Kotlin Multiplatform (KMP)** für die gemeinsame Logik.
- **SwiftUI** für iOS, **Jetpack Compose** für Android.
- Plattformspezifisch über `expect/actual` bzw. Schnittstellen: Keychain/Keystore, Hintergrundjobs, Wallet, Teilen-Menü, Zwischenablage, On-Device-LLM, Benachrichtigungen.

### Vorgeschlagene Module

| Modul | Inhalt | Kandidaten (zu evaluieren) |
|---|---|---|
| `core-transport` | IMAP, SMTP, JMAP, Gmail API, Graph | Ktor (HTTP), eigene IMAP-Implementierung auf Ktor-Sockets oder Rust-Bibliothek via UniFFI |
| `core-mime` | MIME-Parsing, Zitat-Erkennung | eigene KMP-Implementierung |
| `core-store` | Datenbank, Suche | SQLDelight + SQLCipher, FTS5 |
| `core-classify` | Regeln, Modellaufruf | eigene Regeln; Modell plattformspezifisch |
| `core-extract` | QR-Bill, EPC-QR, ZUGFeRD, OTP, Tickets | ZXing/ML Kit bzw. Vision (QR), PDF plattformspezifisch |
| `core-crypto` | OpenPGP, Autocrypt, S/MIME | rPGP (Rust) via UniFFI; S/MIME plattformnah bzw. Bouncy Castle (Android) |

## Konsequenzen

- + Eine Logik-Codebasis, echte native UI und Plattformintegration.
- − IMAP- und Krypto-Bibliotheken für KMP sind weniger ausgereift als auf JVM; Rust-Kern über UniFFI ist eine ernsthafte Alternative (bewährt z. B. bei Delta Chat).
