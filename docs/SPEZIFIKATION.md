# Email-Rethink-App – Spezifikation

| | |
|---|---|
| **Status** | Entwurf v0.1 |
| **Stand** | 15.11.2026 |
| **Plattformen** | iOS (iPhone), Android |
| **Arbeitstitel** | Email-Rethink (Produktname offen) |

---

## 1. Vision

E-Mail gibt es seit über 50 Jahren, die Bedienung hat sich kaum verändert: Der Mensch sortiert, löscht, archiviert und sucht selbst. Diese App dreht das um. **Das Postfach arbeitet sich automatisch ab**, nur was wirklich zählt, landet vorne auf der **Pole Position**. Alles andere wird erkannt, einsortiert, nutzbar gemacht (Rechnung → Banking-App, Ticket → Wallet, Code → Zwischenablage) und wieder entsorgt.

### Leitprinzipien

1. **Kein eigener Server.** Die App spricht direkt mit den Mailservern der Nutzer. Keine Zugangsdaten, keine Inhalte auf Infrastruktur des Herstellers.
2. **Privatsphäre zuerst.** KI läuft standardmässig auf dem Gerät. Cloud-KI nur mit ausdrücklicher Zustimmung (Opt-in).
3. **Etablierte Standards.** Wo es einen offenen Standard gibt, wird er genutzt (IMAP, JMAP, OpenPGP/Autocrypt, S/MIME, List-Unsubscribe, Swiss QR-Bill, PassKit …), keine proprietären Eigenbauten.
4. **Nichts geht unbemerkt verloren.** Automatik ist nachvollziehbar („Warum liegt das hier?“), korrigierbar und rückgängig zu machen. Endgültiges Löschen nur über Fristen.
5. **Native Qualität.** Gemeinsame Logik, aber echte native Oberflächen pro Plattform.

---

## 2. Geklärte Grundsatzentscheidungen

| Thema | Entscheidung | Details |
|---|---|---|
| Architektur | Rein clientseitig, kein Backend in V1. Optionales, zustandsloses Push-Relay als spätere Ausbaustufe | [ADR-001](adr/ADR-001-kein-server.md) |
| Tech-Stack | Kotlin Multiplatform (gemeinsame Logik) + SwiftUI (iOS) + Jetpack Compose (Android) | [ADR-002](adr/ADR-002-kotlin-multiplatform.md) |
| Kontoanbindung V1 | IMAP + SMTP, Gmail API, Microsoft Graph (Microsoft 365/Outlook.com), JMAP. Kein POP3 | [ADR-003](adr/ADR-003-kontoanbindung.md) |
| KI | On-Device zuerst; Cloud-LLM optional per Opt-in für Antwortentwürfe | [ADR-004](adr/ADR-004-ki.md) |
| Verschlüsselung | OpenPGP mit Autocrypt (automatisch zwischen App-Nutzern) **und** S/MIME (Firmenkonten) | [ADR-005](adr/ADR-005-verschluesselung.md) |
| Newsletter | Sammeln, nach Frist automatisch löschen, Abmelden per Knopf (List-Unsubscribe) | Abschnitt 5.3 |
| Rechnungen | „Bezahlt“-Erkennung aus Mailinhalt + Zahlungsbestätigung + manuelle Bestätigung; Teilen an Banking-App | Abschnitt 5.4 |

---

## 3. Begriffe

| Begriff | Bedeutung |
|---|---|
| **Pole Position** | Die oberste, priorisierte Ansicht. Enthält nur echte, persönliche oder wichtige Mails sowie aktuell relevante Tickets und offene Rechnungen. |
| **Ablage** | Automatisch geführte Bereiche: Rechnungen, Verträge, Tickets, Newsletter, Codes, Systemmails. |
| **Kategorie** | Ergebnis der Klassifizierung einer Mail (siehe 5.1). |
| **Abarbeitung** | Die automatische Aktion nach der Klassifizierung (verschieben, extrahieren, Frist setzen, löschen). |
| **App-Nutzer** | Kommunikationspartner, der ebenfalls diese App (oder einen anderen Autocrypt-fähigen Client) nutzt. |

---

## 4. Systemarchitektur

### 4.1 Überblick

```
┌──────────────────────────── Gerät ────────────────────────────┐
│  UI: SwiftUI (iOS)                UI: Jetpack Compose (Android)│
│  ───────────────────────────────────────────────────────────── │
│  Shared Kotlin Multiplatform Core                              │
│   ├─ account      Konten, OAuth2, Autokonfiguration            │
│   ├─ transport    IMAP/SMTP · JMAP · Gmail API · MS Graph      │
│   ├─ sync         Delta-Sync, Offline-Queue, Konfliktlösung    │
│   ├─ store        SQLDelight + SQLCipher, Volltextsuche (FTS5) │
│   ├─ classify     Regeln + On-Device-Modell, Lernen pro Absender│
│   ├─ extract      Rechnung, Ticket, OTP, Termine               │
│   ├─ pipeline     Abarbeitung, Fristen, Undo-Journal           │
│   ├─ crypto       OpenPGP/Autocrypt, S/MIME                    │
│   └─ assistant    Antwortentwürfe (On-Device / Cloud-Opt-in)   │
│  Plattform-Adapter: Keychain/Keystore, Hintergrundjobs, Wallet,│
│  Share-Sheet, Zwischenablage, On-Device-LLM                    │
└────────────────────────────────────────────────────────────────┘
            │ TLS                         │ TLS (nur Opt-in)
   Mailserver der Nutzer           Cloud-LLM-Anbieter
```

Es gibt **keinen** Hersteller-Server. Einzige ausgehende Verbindungen: Mailserver/Provider-APIs, OAuth-Endpunkte der Provider, Autokonfigurations-Quellen, optional Cloud-LLM und optional (Phase 3) Push-Relay.

### 4.2 Hintergrundverarbeitung ohne Server

| Plattform | Mechanismus | Erwartetes Verhalten |
|---|---|---|
| **Android** | Foreground Service mit IMAP IDLE / JMAP Push (EventSource), ergänzend WorkManager | Echtzeit-nah. Laufzeitlimits neuerer Android-Versionen für Foreground Services beachten; Fallback WorkManager-Polling. Nutzer kann Akku-Optimierung für die App deaktivieren. |
| **iOS** | `BGAppRefreshTask` (kurz) + `BGProcessingTask` (länger, z. B. beim Laden) | „Best effort“: iOS bestimmt den Zeitpunkt. Vollständige Abarbeitung spätestens beim Öffnen der App. |
| **Beide** | Abarbeitung beim App-Start zuerst für neueste Mails, dann rückwärts | Pole Position ist nach wenigen Sekunden aktuell. |

**Konsequenz für iOS:** Ohne Server gibt es keine garantierten Sofort-Benachrichtigungen. Das wird im Onboarding offen kommuniziert. Abhilfe in Phase 3: optionales Push-Relay (siehe [ADR-001](adr/ADR-001-kein-server.md)).

### 4.3 Datenhaltung

- Lokale Datenbank (SQLDelight) verschlüsselt mit SQLCipher; Datenbankschlüssel in iOS Keychain bzw. Android Keystore.
- Zugangsdaten und OAuth-Refresh-Tokens ausschliesslich in Keychain/Keystore.
- Volltextsuche lokal (SQLite FTS5); bei JMAP/Gmail/Graph zusätzlich serverseitige Suche.
- Anhänge werden bei Bedarf geladen; Rechnungs- und Ticket-Anhänge werden für die Ablage vorgehalten.
- Kein Analytics, das Inhalte enthält. Absturzberichte nur anonymisiert und mit Opt-in.

---

## 5. Funktionale Anforderungen

### 5.1 Klassifizierung

Jede eingehende Mail erhält genau eine Hauptkategorie:

| Kategorie | Typische Signale | Ziel |
|---|---|---|
| **Wichtig / Persönlich** | Absender in Kontakten oder bisher beantwortet, direkt adressiert, kein Massenversand-Header, Antwort in eigenem Thread | Pole Position |
| **Rechnung** | PDF mit Swiss QR-Bill oder EPC-QR, ZUGFeRD/Factur-X-XML, XRechnung, Begriffe wie „Rechnung“, „Invoice“, Betrag + Fälligkeit | Ablage Rechnungen (offen → Pole Position) |
| **Vertrag** | Anhänge wie Vertrag/Police/AGB, Begriffe „Vertragsbestätigung“, „Laufzeit“, „Kündigungsfrist“, Mails von E-Signatur-Diensten | Ablage Verträge (Akte), Pole Position vor Kündigungsfristen |
| **Ticket / Buchung** | `.pkpass`-Anhang, `.ics`-Anhang, schema.org-Markup (`FlightReservation`, `EventReservation` …), Buchungsnummern | Wallet + Pole Position bis zum Ereignis |
| **Authentifizierung** | Kurzer Code (4–8 Zeichen) + Begriffe wie „Code“, „Bestätigung“, „Login“, „Verification“; Magic Links | Code-Karte, Auto-Entsorgung |
| **Newsletter / Werbung** | `List-Unsubscribe`, `List-Id`, `Precedence: bulk`, bekannte Versanddienste | Ablage Newsletter |
| **Systemmail** | Versand, Bestellungen, Kontoaktivität, Login-Hinweise, Social Media, Server-/Build-Meldungen | Anzeigetafel; Sicherheitsrelevantes auf Pole Position |

**Ablauf:**
1. Deterministische Regeln (Header, Anhangstypen, strukturierte Daten). Schnell, erklärbar.
2. On-Device-Modell für alles, was Regeln nicht eindeutig klären.
3. Nutzerkorrekturen werden pro Absender/Domain gelernt („Mails von X immer wichtig“).
4. Unsichere Fälle (niedrige Konfidenz) landen auf der Pole Position, nie im Müll.

**Anforderungen:**
- Jede automatisch verschobene Mail hat eine Begründung „Warum hier?“ (z. B. „Newsletter: enthält Abmelde-Link, Massenversand“).
- Korrektur mit einer Geste; Korrektur wirkt sofort auf künftige Mails desselben Absenders.
- **Lernmodus** in den ersten 14 Tagen: Die App schlägt Aktionen vor, löscht aber nichts automatisch.

### 5.2 Pole Position

- Zeigt: wichtige/persönliche Mails, offene Rechnungen mit Fälligkeit, Tickets für Ereignisse der nächsten Tage, unsichere Klassifizierungen.
- Sortierung nach Relevanz (Absenderbeziehung, Antwort erwartet, Fälligkeit, Ereignisdatum), nicht rein chronologisch.
- Karten zeigen Kurzzusammenfassung (On-Device-KI) und vorgeschlagene nächste Aktion (antworten, bezahlen, im Wallet öffnen).
- Erledigt-Geste archiviert und entfernt die Karte.

### 5.3 Newsletter

- Erkannte Newsletter werden in der Ablage **Newsletter** gesammelt (nicht in der Pole Position).
- Automatisches Löschen nach einer konfigurierbaren Frist (Standard: 7 Tage, wählbar 1–90 Tage oder „nie“), nur wenn ungelesen bzw. nicht markiert.
- **Abmelden-Knopf** pro Absender:
  - bevorzugt One-Click-Abmeldung nach RFC 8058 (`List-Unsubscribe-Post`),
  - sonst `mailto:` aus `List-Unsubscribe` (RFC 2369),
  - sonst Öffnen des Abmeldelinks im Browser (nach Rückfrage).
- Nach Abmeldung: künftige Mails dieses Absenders sofort in den Papierkorb.
- Übersicht „Deine Newsletter“: Absender, Häufigkeit, zuletzt gelesen, Abmelden mit einem Tipp.
- Kein automatisches Abmelden ohne Nutzeraktion.

### 5.4 Rechnungen

**Erkennung und Extraktion**
- Strukturierte Daten zuerst: Swiss QR-Bill (Zahlteil im PDF), EPC-QR/GiroCode, ZUGFeRD/Factur-X, XRechnung.
- Sonst Textanalyse (On-Device): Rechnungssteller, Betrag, Währung, Rechnungsnummer, Fälligkeitsdatum, Zahlungsart.

**Status „bezahlt“** – automatisch aus mehreren Quellen:
1. **Hinweis in der Rechnungsmail selbst** (häufigster Fall): „wird per Lastschrift / Kreditkarte / LSV / eBill abgebucht“, „bereits bezahlt“, „Betrag dankend erhalten“, „Zahlungsart: Kreditkarte“.
2. **Zahlungsbestätigungs-Mail** desselben Absenders mit passender Rechnungsnummer oder passendem Betrag.
3. **Manuelle Bestätigung** per Wisch-Geste.

Die Herkunft des Status wird angezeigt („bezahlt laut Mail vom 3.11.“).

**Bezahlen über die Banking-App**
- Aktion **„An Banking-App senden“**: Rechnungs-PDF bzw. QR-Code über das System-Teilen-Menü (iOS Share Sheet / Android Sharesheet) an die Banking-App übergeben.
- Hat die Rechnung einen Swiss QR-Bill- oder EPC-Zahlteil, wird zusätzlich der **QR-Code isoliert und gross angezeigt** – zum Scannen mit einer zweiten Kamera bzw. als Bild zum Teilen an Banking-Apps, die QR-Bilder importieren.
- Optional: Zahlungsdaten (IBAN, Referenz, Betrag) einzeln kopierbar.
- Nach dem Teilen fragt die App beim nächsten Öffnen nach: „Rechnung von X bezahlt?“

**Archivierung**
- Bezahlte Rechnungen werden automatisch archiviert, **gruppiert nach Rechnungssteller**.
- Ansicht pro Rechnungssteller: alle Rechnungen, Summen pro Jahr, Export (PDF-Sammlung/CSV) z. B. für die Steuererklärung.
- Offene Rechnungen bleiben auf der Pole Position, Erinnerung vor Fälligkeit (lokale Benachrichtigung).
- Archivierung bedeutet: Mail im Server-Archivordner (bzw. Gmail-Label / Graph-Ordner) plus lokale Indexierung. Keine Mail wird für die Rechnungsablage gelöscht.

### 5.5 Tickets und Buchungen

- `.pkpass`-Anhänge: Knopf „Zu Apple Wallet hinzufügen“ (PassKit) bzw. „In Google Wallet speichern“.
- Tickets ohne Wallet-Pass (PDF-Ticket, Buchungsbestätigung): Ticket-Karte mit Datum, Ort, Buchungsnummer, QR-/Barcode-Ansicht in voller Helligkeit; auf Android als Google-Wallet-Generic-Pass, sofern umsetzbar.
- `.ics` bzw. erkannte Termine: Ein Tipp zum Übernehmen in den Kalender.
- Ticket erscheint ab 48 h (konfigurierbar) vor dem Ereignis auf der Pole Position, danach automatische Archivierung.

### 5.6 Authentifizierungs-Mails (Codes)

- Code wird erkannt und als **Code-Karte** ganz oben angezeigt, mit grossem **Kopieren**-Knopf; optional lokale Benachrichtigung mit Kopier-Aktion.
- Zwischenablage mit Ablauf: iOS `UIPasteboard` mit `localOnly` und `expirationDate`; Android `ClipDescription.EXTRA_IS_SENSITIVE` (keine Vorschau, kein Cloud-Sync), Leeren nach Ablauf.
- **Gültigkeit:** aus dem Text erkannt („gültig für 10 Minuten“), sonst Standard 15 Minuten.
- **Entsorgung:** nach Ablauf Karte entfernen und Mail in den Papierkorb (bzw. sofort, wenn ein neuerer Code desselben Absenders eintrifft).
- Magic Links („Anmelden“-Knopf) werden analog behandelt, mit Warnung bei unbekannter Absenderdomain.
- Sicherheitsregel: Codes werden nie an die Cloud-KI gesendet.

### 5.7 Antwort-Assistent

- Vorschläge für Kurzantworten (On-Device) direkt auf der Karte.
- Ausführlicher Entwurf auf Wunsch: Ton (förmlich/locker), Länge, Sprache; berücksichtigt den Verlauf des Threads.
- **On-Device** standardmässig (Apple Foundation Models auf unterstützten iPhones, Gemini Nano über Android AICore auf unterstützten Geräten).
- **Cloud-LLM nur mit Opt-in**, pro Konto abschaltbar; vor dem ersten Senden wird angezeigt, welche Daten übertragen werden. Verschlüsselte Mails werden nie an die Cloud gesendet.
- Der Entwurf wird nie automatisch versendet.

### 5.8 Neuartige Verlaufsdarstellung (History)

> Grundkonzept „Chaos mit Zoomstufen“ entschieden am 15.11.2026; Herleitung und alle Varianten im [Brainstorming](konzepte/VERLAUF-BRAINSTORM.md). Details werden per Prototyp validiert.

Gewählte Bausteine: Chat-Ansicht, Zeitstrahl pro Kontakt, Zusammenfassung zuerst, Themen-Gruppierung – verbunden über **semantischen Zoom**:

| Zoomstufe | Darstellung |
|---|---|
| Ganz raus | **Themen-Wabe**: Vorgänge als Hexagon-Waben, automatisch gruppiert (auch über Threads und Absender hinweg); Grösse = Aktivität, Farbe = Zustand |
| Mittel | **Zeitstrahl** pro Kontakt/Thema als **Quipu-Schnüre** (Knotenart = Mail, Rechnung, Ticket, Termin); im Querformat als **Partitur** mit einer Zeile pro Person |
| Ganz rein | **Chat-Ansicht**: Zitat-Ketten entfernt, nur neuer Inhalt pro Nachricht |

Querschnittlich:
- **Zusammenfassung zuerst:** Kopfkarte jedes Vorgangs mit „Worum geht es, Stand, was ist offen, wer ist dran?“ sowie hervorgehobenen Zusagen und Entscheidungen (On-Device-KI).
- **Hexagramm-Glyph:** kompaktes Status-Symbol aus sechs Linien (z. B. du bist dran, offen, Geld im Spiel, Termin, Anhang, verschlüsselt) neben jedem Vorgang, in Widgets und auf der Uhr.
- **Drehgeste:** hochkant = lesen/antworten, quer = Überblick (Partitur/Quipu).
- **Swipe-Triage** auf der Pole Position; Tippen blättert wie Stories durch den Verlauf eines Vorgangs.
- **Jahresringe** im Kopf der Kontaktseite (Dauer und Intensität der Beziehung).
- **Blackboard-Modus** für komplexe Vorgänge (v. a. Tablet): Pinnwand mit automatisch gezogenen Fäden zwischen zusammengehörenden Mails, Personen, Rechnungen und Terminen.

### 5.9 Konten

- Mehrere Konten, gemeinsame Pole Position mit Kontofarbe.
- **Einrichtung:** E-Mail-Adresse eingeben → automatische Erkennung über Provider-Erkennung (Gmail, Microsoft), Mozilla-Autokonfiguration (ISPDB/autoconfig), DNS-SRV-Einträge (RFC 6186) und JMAP-Session-Discovery (`/.well-known/jmap`); manuelle Eingabe als Fallback.
- **Anmeldung:** OAuth2 mit PKCE über den Systembrowser (RFC 8252) für Gmail, Microsoft und alle Provider mit OAuth; App-Passwort/Passwort für klassisches IMAP. TLS Pflicht (implizites TLS bevorzugt, RFC 8314); keine unverschlüsselten Verbindungen.
- Ordner-Mapping: Server-Ordner bzw. Labels für Archiv, Papierkorb, Newsletter, Rechnungen werden automatisch angelegt oder zugeordnet (IMAP SPECIAL-USE, RFC 6154), damit der Zustand auch in anderen Mail-Clients sichtbar ist.

### 5.10 Sichere Kommunikation zwischen App-Nutzern

- **Autocrypt (Level 1)**: Jede ausgehende Mail trägt den `Autocrypt`-Header mit dem öffentlichen OpenPGP-Schlüssel. Erkennt die App bei einem Kontakt einen Autocrypt-Header, wird künftig automatisch verschlüsselt (PGP/MIME, RFC 3156; OpenPGP nach RFC 9580).
- Zusätzlich Schlüsselsuche über Web Key Directory (WKD).
- **S/MIME** (RFC 8551) für Firmenkonten: Import von Zertifikat/Schlüssel (PKCS#12), Verschlüsseln und Signieren, sobald ein Zertifikat des Empfängers bekannt ist.
- Regel bei beidem verfügbar: Firmenrichtlinie bzw. Kontoeinstellung bestimmt; Standard bei Privatkonten OpenPGP.
- Statusanzeige pro Mail/Kontakt: verschlüsselt, signiert, Schlüssel verifiziert (Vergleich per QR-Code oder Fingerprint).
- **Betreff-Schutz** („Protected Headers“) für verschlüsselte Mails.
- **Schlüssel-Backup/Übertragung** auf neues Gerät: Autocrypt Setup Message bzw. verschlüsselter Export.
- Private Schlüssel nur in Keychain/Keystore (wo möglich hardwaregestützt).
- Verschlüsselte Inhalte werden lokal entschlüsselt klassifiziert; nie an Cloud-KI.

### 5.11 Sicherheit beim Löschen

- Automatisches Löschen = Verschieben in den Papierkorb des Servers, nie direktes `EXPUNGE`.
- Lokales **Undo-Journal**: jede automatische Aktion 30 Tage rückgängig machbar.
- Mails mit Konfidenz unter Schwelle werden nie automatisch gelöscht.
- Tageszusammenfassung (optional): „Heute erledigt: 12 Newsletter, 3 Codes, 1 Rechnung archiviert.“

### 5.12 Verträge

- Eckdaten werden aus Mail und Anhang extrahiert: Vertragspartner, Vertragsnummer, Beginn, Laufzeit, automatische Verlängerung, Kündigungsfrist, nächster Kündigungstermin, Kosten.
- Darstellung als **Akte** mit Lebenslinie (Abschluss, Änderungen, Kündigungsfenster).
- Bei AGB- oder Preisänderungen wird angezeigt, was sich geändert hat.
- Erinnerungen vor Kündigungsfristen (z. B. 30 und 7 Tage vorher), Vertrag dann auf der Pole Position.
- Kündigen-Assistent: KI entwirft das Kündigungsschreiben; Versand nur durch den Nutzer.
- Rechnungen desselben Vertragspartners werden mit der Akte verknüpft.
- Archiv nach Lebensbereich (Wohnen, Versicherung, Mobilität, Telekom, Arbeit, Abos) mit Monatskosten-Summe.

### 5.13 Darstellung pro Mail-Typ

Jeder Typ hat eine eigene Darstellung nach gemeinsamer Grammatik (Kopf · Hauptaktion · Lebenszyklus · Verlaufsform). Details im Konzept [Darstellung pro Mail-Typ](konzepte/DARSTELLUNG-PRO-TYP.md).

| Typ | Leitbild | Hauptaktion |
|---|---|---|
| Korrespondenz | Gespräch (Chat + Zusammenfassung) | Antworten |
| Rechnung | Beleg mit Status-Stempel und Preisvergleich | An Banking-App |
| Vertrag | Akte mit Lebenslinie und Kündigungsfenstern | Frist merken / Kündigen |
| Systemmail | Anzeigetafel, nur aktueller Zustand pro Quelle | – |
| Newsletter | Kiosk mit Leseansicht, Tracking-Schutz, Wochen-Digest | Lesen / Abmelden |
| Ticket | Bordkarte | In Wallet |
| Code | Countdown | Kopieren |

---

## 6. Nicht-funktionale Anforderungen

| Bereich | Anforderung |
|---|---|
| Performance | Kaltstart bis Pole Position sichtbar < 1,5 s (aus Cache); Klassifizierung < 200 ms pro Mail auf Mittelklasse-Gerät (Regeln + kleines Modell). |
| Offline | Lesen, Suchen, Entwürfe, Aktionen offline; Synchronisation bei Verbindung. |
| Datenschutz | Keine Hersteller-Server, keine Inhalte in Analytics; DSG (CH) und DSGVO (EU) konform; Datenschutzerklärung listet alle Fremd-Endpunkte. |
| Sicherheit | TLS 1.2+ mit Zertifikatsprüfung, verschlüsselte lokale DB, Sperre per Face ID/Touch ID/Biometrie optional, OWASP MASVS als Prüfgrundlage. |
| Barrierefreiheit | VoiceOver/TalkBack, dynamische Schriftgrössen, Kontraste nach WCAG 2.2 AA. |
| Sprachen | Deutsch und Englisch zum Start; Klassifizierung mehrsprachig (mind. DE, EN, FR, IT). |
| Akku | Hintergrundarbeit gebündelt; kein dauerhafter Wake-Lock ausserhalb des Android-Foreground-Service. |

---

## 7. Roadmap

| Phase | Inhalt |
|---|---|
| **0 – Grundlagen** | KMP-Projekt, IMAP/SMTP-Sync, lokale DB, einfache Inbox, OAuth für Gmail/Microsoft |
| **1 – MVP** | Klassifizierung (Regeln + On-Device), Pole Position, Newsletter-Ablage mit Frist und Abmelden, Code-Karten, Rechnungen inkl. „An Banking-App senden“, Tickets → Wallet, Undo-Journal, Lernmodus |
| **2 – Kommunikation** | Antwort-Assistent (On-Device, Cloud-Opt-in), JMAP, Gmail API und Graph als native Transporte, Autocrypt/OpenPGP, S/MIME, neue Verlaufsdarstellung |
| **3 – Ausbau** | Optionales zustandsloses Push-Relay (iOS-Sofortbenachrichtigung für Gmail/Microsoft/JMAP), Rechnungs-Export, Widgets, Apple Watch / Wear OS |

---

## 8. Verwandte Dokumente

- [Offene Punkte](OFFENE-PUNKTE.md)
- [ADR-001 Kein eigener Server](adr/ADR-001-kein-server.md)
- [ADR-002 Kotlin Multiplatform](adr/ADR-002-kotlin-multiplatform.md)
- [ADR-003 Kontoanbindung](adr/ADR-003-kontoanbindung.md)
- [ADR-004 KI](adr/ADR-004-ki.md)
- [ADR-005 Verschlüsselung](adr/ADR-005-verschluesselung.md)
- [Standards-Referenz](STANDARDS.md)
- [Brainstorming Verlaufsdarstellung](konzepte/VERLAUF-BRAINSTORM.md)
- [Darstellung pro Mail-Typ](konzepte/DARSTELLUNG-PRO-TYP.md)
