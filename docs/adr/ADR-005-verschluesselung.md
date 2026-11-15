# ADR-005: Verschlüsselung – OpenPGP/Autocrypt und S/MIME

**Status:** Angenommen · 15.11.2026

## Kontext

Kommunizieren zwei Nutzer der App miteinander, soll automatisch auf sichere Kommunikation umgestellt werden – mit etablierten Standards und ohne eigenen Server.

## Entscheidung

1. **OpenPGP mit Autocrypt Level 1** als Standard:
   - Schlüsselpaar wird bei Kontoeinrichtung automatisch erzeugt.
   - Jede ausgehende Mail trägt den `Autocrypt`-Header; empfangene Header werden gespeichert.
   - Liegen Schlüssel aller Empfänger vor und haben alle `prefer-encrypt=mutual`, wird automatisch verschlüsselt.
   - Kompatibel mit anderen Autocrypt-Clients (z. B. Thunderbird, Delta Chat, K-9/Thunderbird für Android) – es braucht also keine Erkennung „nutzt dieselbe App“ und kein Verzeichnis beim Hersteller.
   - Ergänzend: Web Key Directory (WKD) für Schlüsselsuche.
2. **S/MIME** zusätzlich für Firmenumgebungen mit Zertifikatsinfrastruktur (Import PKCS#12, Empfängerzertifikate aus signierten Mails).
3. Pro Konto wählbar, welches Verfahren bevorzugt wird; Standard: OpenPGP bei Privatkonten, S/MIME wenn ein Zertifikat importiert ist.

## Konsequenzen

- + Kein Server, offene Standards, interoperabel.
- − Autocrypt schützt gegen passive Mitleser, nicht automatisch gegen aktive Angreifer → optionale Schlüsselverifikation per QR-Code.
- − Serverseitige Suche funktioniert bei verschlüsselten Mails nicht; lokale Suche nach Entschlüsselung.
- − Mehrgeräte-Nutzung erfordert Schlüsselübertragung (Autocrypt Setup Message).
