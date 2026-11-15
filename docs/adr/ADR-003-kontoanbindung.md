# ADR-003: Kontoanbindung

**Status:** Angenommen · 15.11.2026

## Kontext

Gefragt war: IMAP(S), POP/SMTP, Exchange – oder was ist aktuell?

- **POP3** lädt nur herunter, kennt keine Ordner und keinen Sync zwischen Geräten. Für automatische Ablage ungeeignet.
- **Exchange Web Services (EWS)** wird von Microsoft für Exchange Online abgekündigt; Nachfolger ist **Microsoft Graph**.
- **Gmail** unterstützt IMAP, bietet aber über die **Gmail API** Labels, Delta-Sync (History-ID) und Push.
- **JMAP** (RFC 8620/8621) ist der moderne, HTTP/JSON-basierte Nachfolger von IMAP, u. a. bei Fastmail und Stalwart.

## Entscheidung

V1 unterstützt:

| Anbindung | Einsatz |
|---|---|
| **IMAP4rev1/rev2 + SMTP Submission** | Universeller Standard für alle übrigen Provider (GMX, web.de, Bluewin, Proton Bridge, eigene Server …). Erweiterungen: IDLE, CONDSTORE/QRESYNC, SPECIAL-USE, MOVE, UIDPLUS. |
| **Gmail API** | Gmail- und Google-Workspace-Konten. |
| **Microsoft Graph** | Microsoft 365, Outlook.com, Hotmail. |
| **JMAP** | Provider mit JMAP-Unterstützung. |

**Nicht unterstützt:** POP3, EWS, Exchange ActiveSync.

**Authentifizierung:** OAuth2 mit PKCE über den Systembrowser (für IMAP/SMTP per SASL `XOAUTH2`/`OAUTHBEARER`), sonst Passwort/App-Passwort. Nur TLS.

## Konsequenzen

- + Moderne, zukunftssichere Anbindung; Delta-Sync spart Akku und Daten.
- − Vier Transportwege; die gemeinsame Abstraktion (`MailTransport`) muss Labels (Gmail) und Ordner (IMAP/Graph/JMAP-Mailboxen) einheitlich abbilden.
- − Gmail API mit eingeschränkten Scopes erfordert Googles Sicherheitsprüfung (CASA) vor der Veröffentlichung – frühzeitig einplanen. Bis dahin Gmail über IMAP + OAuth.
