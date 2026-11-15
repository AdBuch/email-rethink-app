# ADR-004: KI – On-Device zuerst, Cloud optional

**Status:** Angenommen · 15.11.2026

## Entscheidung

| Aufgabe | Standard | Optional (Opt-in) |
|---|---|---|
| Klassifizierung | Regeln + kleines On-Device-Modell | – (bleibt immer lokal) |
| Extraktion (Rechnung, Ticket, Code) | Regeln, Parser, QR-Erkennung, On-Device-LLM | – |
| Zusammenfassungen | On-Device-LLM | Cloud-LLM |
| Antwortentwürfe | On-Device-LLM (Kurzantworten) | Cloud-LLM (ausführliche Entwürfe) |

- **iOS:** Apple Foundation Models Framework auf unterstützten Geräten.
- **Android:** Gemini Nano über AICore / ML Kit GenAI auf unterstützten Geräten.
- **Ältere Geräte:** Regeln + kompaktes Klassifikationsmodell (z. B. Core ML / TensorFlow Lite); Antwortentwürfe dann nur mit Cloud-Opt-in.
- **Cloud-LLM:** Anbieter austauschbar hinter einer Schnittstelle; Nutzer wählt bewusst, pro Konto abschaltbar.

## Regeln

- Nie an die Cloud: verschlüsselte Mails, Authentifizierungscodes, Zugangsdaten.
- Vor dem ersten Cloud-Aufruf: klare Anzeige, welche Daten an wen gehen.
- KI versendet nie selbstständig Mails.
