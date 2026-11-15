# ADR-007: Verschlüsselte lokale Maildatenbank und optionale App-Sperre

- **Status:** Angenommen (Produkt- und Sicherheitsanforderung); technische Integration noch zu verifizieren
- **Datum:** 2026-11-15
- **Entscheidung:** Offener Punkt 15; Nutzerentscheidung **C**
- **Bezug:** ADR-001, ADR-002, ADR-006

## Kontext

E-Mails und Kontometadaten enthalten sensible Informationen. Die App benötigt lokale persistente Daten, darf aber keinen Hersteller-Backenddienst voraussetzen. Der Nutzer soll selbst entscheiden können, ob zusätzlich zur Gerätesperre eine App-Sperre verlangt wird.

## Entscheidung

1. Die lokale Maildatenbank wird **immer verschlüsselt** gespeichert. Vorgesehener Stack: SQLDelight für typisierte Abfragen und SQLCipher für Verschlüsselung auf Datenbankebene. Kein unverschlüsselter Fallback für reale Maildaten.
2. Der Datenbankschlüssel wird kryptografisch zufällig pro Installation erzeugt und geschützt abgelegt. Android: Android Keystore, iOS: Keychain mit geeigneten Zugriffseinstellungen. Der rohe Schlüssel gehört weder in Quellcode, Logs, Backups im Klartext noch in Konfigurationsdateien.
3. Die **zusätzliche App-Sperre ist optional** (Entscheidung C). Standard: deaktiviert; die normale Geräteentsperrung genügt. In den Einstellungen kann eine Entsperrung per verfügbarer Gerätebiometrie oder Geräte-PIN/-Code aktiviert werden. Keine eigene PIN-Datenbank oder Speicherung biometrischer Merkmale.
4. App-Sperre und Datenbankverschlüsselung sind **unterschiedliche Schutzschichten**. Die App-Sperre schützt die Oberfläche gegen beiläufigen Zugriff auf einem bereits entsperrten Gerät; sie ersetzt nicht die Datenbankverschlüsselung. Ohne aktivierte App-Sperre bleibt die Datenbank ebenfalls verschlüsselt.
5. Beim Aktivieren der App-Sperre müssen Hintergrund-Synchronisation, Benachrichtigungen und sichere Wiederaufnahme definiert werden. Geheimnisse dürfen nicht über Benachrichtigungsvorschauen oder Debug-Logs preisgegeben werden. Bei gesperrter App dürfen keine Mailinhalte sichtbar sein.
6. Bei Biometrieänderung, Gerätecode-Entfernung, Schlüsselverlust und Gerätewechsel ist ein sicherer Wiederherstellungs-/Neuanmeldepfad vorzusehen. Keine unbemerkte Rückstufung auf unverschlüsselte Speicherung.

## Technische Verifikation vor Speicherung echter Mails

- SQLDelight-Treiber und SQLCipher-Bibliothek für Android und iOS konkret auswählen; API-, ABI- und Lizenzkompatibilität nachweisen.
- Verschlüsselten Datenbank-Open/Create/Migration-/Close-Pfad auf beiden Plattformen testen, inklusive falschem Schlüssel und gesperrtem Gerät.
- Android-Keystore- und iOS-Keychain-Schlüsselverwaltung testen; sicherstellen, dass keine Klartext-DB, Journal-/WAL-Dateien oder temporäre Exporte zurückbleiben.
- Biometrie/Geräte-PIN über die nativen Betriebssystem-APIs testen, inklusive Abbruch und Fallback.
- Backup-, Gerätewechsel- und Datenlöschstrategie dokumentieren.
- Reproduzierbare Android-/iOS-Builds und automatisierte Tests durchführen.

## Konsequenzen

**Vorteile:** Datenschutz standardmässig, zusätzliche Zugangshürde nach Wunsch, keine eigenen Authentifizierungsgeheimnisse.

**Kosten:** Komplexere Schlüsselverwaltung, Datenbanktreiberintegration und Recovery-/Backup-Konzept. Die konkrete Bibliothekskombination ist bis zum erfolgreichen Spike **nicht freigegeben**.

## Offene Implementierungsdetails

- Exakte SQLCipher-/SQLDelight-Treiberkombination und Lizenzprüfung.
- App-Sperr-Timeout (sofort, nach Hintergrundwechsel oder konfigurierbar).
- Verhalten der Benachrichtigungsvorschau und Hintergrund-Synchronisation bei aktiver Sperre.
- Backup und Wiederherstellung bei Schlüsselverlust.
