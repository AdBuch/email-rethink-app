# ADR-001: Kein eigener Server, optionales Push-Relay später

**Status:** Angenommen · 15.11.2026

## Kontext

Die App soll Mails automatisch abarbeiten. Andere Mail-Apps lösen die Hintergrundverarbeitung unterschiedlich:

- **Apple Mail** hat Systemprivilegien und läuft dauerhaft im Hintergrund – für Drittanbieter nicht verfügbar.
- **Spark, Outlook, Edison u. a.** betreiben eigene Server, die das Postfach überwachen (mit Zugangsdaten bzw. OAuth-Tokens der Nutzer) und per Apple Push Notification Service (APNs) benachrichtigen.
- **Ohne Server** weckt iOS eine App nur über `BGAppRefreshTask`/`BGProcessingTask`, zu Zeitpunkten, die das System anhand des Nutzungsverhaltens festlegt.
- **Android** erlaubt dauerhafte Verbindungen (IMAP IDLE) über einen Foreground Service, ohne Server (Beispiel: FairEmail).

## Entscheidung

- **V1 ohne jeden Hersteller-Server.** Alle Verbindungen gehen direkt vom Gerät zum Mailserver.
- iOS: Abarbeitung beim Öffnen der App sowie opportunistisch im Hintergrund. Das wird im Onboarding transparent erklärt.
- Android: Foreground Service mit IMAP IDLE / JMAP Push, Fallback WorkManager.
- **Phase 3, optional:** ein **zustandsloses Push-Relay**, das
  - Webhook-Signale der Provider empfängt (Gmail `users.watch` über Pub/Sub, Microsoft Graph Change Notifications, JMAP PushSubscription),
  - daraus ausschliesslich ein inhaltsloses Silent-Push (`content-available`) an das Gerät macht,
  - **keine** Zugangsdaten, Tokens oder Mailinhalte speichert oder sieht,
  - quelloffen und selbst hostbar ist.
- Für klassisches IMAP ist ein Relay ohne Zugangsdaten nicht möglich; IMAP-Konten bleiben auf iOS bei „Best effort“.

## Konsequenzen

- + Höchste Privatsphäre, kein Betriebsaufwand, keine Haftung für Nutzerdaten auf Servern.
- − Auf iOS keine garantierte Sofort-Benachrichtigung in V1.
- − Gmail-Pub/Sub und Graph-Webhooks benötigen beim Relay eine registrierte Cloud-Projekt-/App-Identität des Herstellers.
