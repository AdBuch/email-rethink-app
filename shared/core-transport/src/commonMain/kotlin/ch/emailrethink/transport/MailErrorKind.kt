package ch.emailrethink.transport

/** Fehlerklassen des Transports; spiegeln `MailError` des Rust-Kerns. */
enum class MailErrorKind(val userMessage: String) {
    INVALID_INPUT("Bitte Server, Port, Benutzername und Passwort prüfen."),
    PLAINTEXT_PORT_REJECTED(
        "Port 143 ist der unverschlüsselte IMAP-Port und wird nicht unterstützt. Bitte den TLS-Port des Anbieters verwenden (meist 993).",
    ),
    NETWORK("Der Mailserver ist nicht erreichbar. Bitte Internetverbindung, Servername und Port prüfen."),
    TIMEOUT("Der Mailserver antwortet nicht rechtzeitig. Bitte später erneut versuchen."),
    TLS_CERTIFICATE_UNTRUSTED(
        "Das Sicherheitszertifikat des Servers ist nicht vertrauenswürdig. Es wurde keine Verbindung aufgebaut und kein Passwort gesendet.",
    ),
    TLS_CERTIFICATE_EXPIRED(
        "Das Sicherheitszertifikat des Servers ist abgelaufen oder noch nicht gültig. Es wurde kein Passwort gesendet. Bitte auch Datum und Uhrzeit des Geräts prüfen.",
    ),
    TLS_HOSTNAME_MISMATCH(
        "Das Sicherheitszertifikat gehört nicht zu diesem Servernamen. Es wurde kein Passwort gesendet. Bitte den Servernamen prüfen.",
    ),
    TLS_FAILED(
        "Es konnte keine verschlüsselte Verbindung aufgebaut werden. Bitte prüfen, ob der Port der TLS-Port des Anbieters ist (meist 993).",
    ),
    AUTHENTICATION_FAILED(
        "Anmeldung abgelehnt. Bitte Benutzername und Passwort prüfen – manche Anbieter verlangen ein App-Passwort.",
    ),
    PROTOCOL("Der Mailserver hat unerwartet geantwortet. Bitte später erneut versuchen."),
    UID_VALIDITY_CHANGED("Der Posteingang wurde auf dem Server neu aufgebaut. Bitte die Liste aktualisieren."),
    MESSAGE_NOT_FOUND("Diese Nachricht ist auf dem Server nicht mehr vorhanden."),
    MESSAGE_TOO_LARGE("Diese Nachricht ist zu gross, um sie anzuzeigen."),
    UNKNOWN("Unbekannter Fehler beim Abrufen der Mails.");

    companion object {
        /**
         * Ordnet den Variantennamen des Rust-Fehlers (z. B. `TlsFailed`, `tlsFailed`,
         * `TLS_FAILED`) der Fehlerklasse zu – unabhängig von der Schreibweise der
         * jeweiligen UniFFI-Bindings.
         */
        fun fromRustName(name: String?): MailErrorKind {
            val wanted = normalize(name ?: return UNKNOWN)
            return entries.firstOrNull { normalize(it.name) == wanted } ?: UNKNOWN
        }

        /** Meldung für die Oberfläche; für Swift bequemer als der Umweg über das Enum. */
        fun userMessageForRustName(name: String?): String = fromRustName(name).userMessage

        private fun normalize(value: String): String =
            value.substringBefore('(').filter { it.isLetterOrDigit() }.lowercase()
    }
}
