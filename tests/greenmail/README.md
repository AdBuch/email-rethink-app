# GreenMail: IMAPS-Integrationstests (Vorbereitung)

```sh
docker compose -f tests/greenmail/compose.yaml up -d
docker compose -f tests/greenmail/compose.yaml down
```

Der Testserver soll ausschliesslich lokal erreichbar sein. Die Compose-Datei ist **noch keine ausführbare Integrations-Test-Suite**; Testbenutzer, Testnachrichten, Zertifikatsvertrauen und die Rust-Testfälle fehlen.

**Sicherheit:** GreenMail verwendet in der Testkonfiguration möglicherweise ein selbstsigniertes Zertifikat. Der produktive Rust-Client darf dafür die Zertifikatsprüfung **nicht** abschalten. Für Integrationstests muss eine explizite, nur im Test verfügbare CA-/Trust-Store-Injektion implementiert werden. Niemals private Schlüssel oder echte Mailzugänge einchecken.

Die genaue GreenMail-Konfiguration und der Containerstart sind noch zu verifizieren.
