# Offene Punkte

Stand 15.11.2026. Diese Punkte sind noch nicht entschieden; die Spezifikation trifft dafür vorläufige Annahmen.

| # | Thema | Frage | Vorläufige Annahme |
|---|---|---|---|
| 1 | Verlaufsdarstellung | Grundkonzept entschieden („Chaos mit Zoomstufen“, Spez. 5.8). Offen: Quipu oder Partitur im Querformat? Welche sechs Linien hat der Hexagramm-Glyph? | Per Prototyp mit 5–8 Testpersonen entscheiden ([Brainstorming](konzepte/VERLAUF-BRAINSTORM.md)) |
| 2 | Produktname | Wie heisst die App? | Arbeitstitel „Email-Rethink“ |
| 3 | Geschäftsmodell | Kostenlos, Einmalkauf, Abo? Wer zahlt Cloud-KI? | offen |
| 4 | Cloud-LLM-Anbieter | Welcher Anbieter / eigener API-Schlüssel des Nutzers? | austauschbare Schnittstelle |
| 5 | Mindest-OS-Versionen | Welche iOS-/Android-Versionen? On-Device-LLM nur auf neueren Geräten | iOS 17+, Android 10+; LLM-Funktionen nur wo verfügbar |
| 6 | Newsletter-Frist | Standardwert | 7 Tage |
| 7 | Code-Gültigkeit | Standard ohne Angabe im Text | 15 Minuten |
| 8 | Ticket-Vorlauf | Ab wann auf der Pole Position? | 48 Stunden |
| 9 | eBill (CH) | Integration in eBill-Prozess der Banken nötig/möglich? | nicht in V1 |
| 10 | Rechnungs-Export | Format für Steuer/Buchhaltung | PDF-Sammlung + CSV |
| 11 | Repo-Hosting | Wo soll das Repo liegen (GitHub, GitLab …)? | lokal, Übergabe als Archiv; inzwischen GitHub |
| 12 | Lizenz | Open Source (z. B. für Vertrauen bei Krypto/Relay) oder proprietär? | Relay quelloffen; App offen |
| 13 | Gmail-Verifizierung | Budget/Zeit für Googles Sicherheitsprüfung (CASA) | Gmail zunächst über IMAP + OAuth |
| 14 | IMAP-KMP-Bibliothek | Welche TLS-validierende, auf Android und iOS lauffähige Implementierung wird für IMAP verwendet? | Kandidaten aus ADR-002 evaluieren; keine plattformspezifische Sackgasse festlegen |
| 15 | SQLCipher-KMP-Integration | Welche SQLDelight-Treiber-/SQLCipher-Kombination erfüllt die Verschlüsselungsanforderung auf beiden Plattformen? | Vor Verarbeitung realer Maildaten klären und testen |
| 16 | Build-Toolchain | Welche exakten Versionen von Kotlin, Gradle, Android Gradle Plugin und Xcode werden als unterstützte Matrix festgelegt? | Vorläufige Gradle-Versionen im Grundgerüst, Build noch nicht verifiziert |
