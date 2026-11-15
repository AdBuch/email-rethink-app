# GreenMail: IMAPS-Integrationstests

```sh
tests/greenmail/run.sh
```

Das Skript

1. erzeugt mit `gen-certs.sh` eine **Wegwerf-Test-CA** und zwei Serverzertifikate für `localhost` (eines gültig, eines seit 2020 abgelaufen) – bei jedem Lauf neu, nie eingecheckt;
2. startet zwei GreenMail-Container (`compose.yaml`), nur an `127.0.0.1` gebunden;
3. führt `cargo test --features test-ca --test greenmail` in `rust/mail-transport` aus;
4. stoppt die Container wieder.

| Port | Zweck |
|---|---|
| 3993 | IMAPS mit gültigem Testzertifikat |
| 4993 | IMAPS mit abgelaufenem Testzertifikat |
| 3143 | Klartext-IMAP, nur für den Negativtest „kein Klartext-Fallback“ |

## Zertifikatspolitik

Die Zertifikatsprüfung wird **nirgends abgeschaltet**. Die Test-CA wird nur über das Cargo-Feature `test-ca` als zusätzlicher Vertrauensanker zugelassen; dieses Feature ist in App-Builds nicht aktiv, der Code dafür ist dort nicht einkompiliert. Ein Test ruft zusätzlich die produktive Funktion `fetch_inbox` ohne Test-CA auf und erwartet die Ablehnung des Zertifikats.

## Zugangsdaten

Benutzer und Passwort in `compose.yaml` existieren nur in diesem lokalen Testserver. Keine echten Konten in Tests verwenden.
