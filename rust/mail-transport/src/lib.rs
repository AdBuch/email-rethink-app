//! Gemeinsamer IMAPS-Transportkern (ADR-006).
//!
//! Grundsätze:
//! - ausschliesslich implizites TLS; es gibt keinen Klartext- und keinen STARTTLS-Pfad,
//! - Zertifikatskette und Hostname werden immer geprüft, es gibt keinen Schalter zum Abschalten,
//! - Zugangsdaten werden weder gespeichert noch geloggt,
//! - Synchronisation über UIDVALIDITY + UID, nie über Sequenznummern.

mod imap;
mod mime;
mod tls;

#[cfg(feature = "test-ca")]
pub mod testing;

use zeroize::Zeroizing;

uniffi::setup_scaffolding!();

/// Höchstzahl an Kopfzeilen pro Abruf.
pub const MAX_HEADERS: u32 = 200;
/// Grösste Nachricht, die zur Anzeige vollständig geladen wird.
pub const MAX_BODY_BYTES: u32 = 10 * 1024 * 1024;

/// Verbindungsdaten eines IMAP-Kontos. Das Passwort gehört bewusst nicht dazu.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ImapAccount {
    pub host: String,
    pub port: u16,
    pub username: String,
}

/// Kopfdaten einer Nachricht aus `UID FETCH (UID FLAGS INTERNALDATE ENVELOPE)`.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MailHeader {
    pub uid: u32,
    pub sender_name: Option<String>,
    pub sender_address: Option<String>,
    pub subject: Option<String>,
    /// INTERNALDATE als Unix-Zeit in Sekunden; 0, wenn der Server keines liefert.
    pub internal_date_epoch_seconds: i64,
    pub message_id: Option<String>,
    pub seen: bool,
}

/// Momentaufnahme des Posteingangs.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct InboxSnapshot {
    pub uid_validity: u32,
    pub folders: Vec<String>,
    /// Neueste zuerst (absteigende UID).
    pub headers: Vec<MailHeader>,
}

/// Dekodierter Inhalt einer Nachricht.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MailBody {
    pub text: Option<String>,
    /// Unverändertes HTML des Absenders. Darf nur ohne JavaScript und ohne
    /// externe Inhalte dargestellt werden.
    pub html: Option<String>,
}

/// Fehlerklassen für verständliche Meldungen in den Apps.
#[derive(Debug, Clone, Copy, PartialEq, Eq, thiserror::Error, uniffi::Error)]
pub enum MailError {
    #[error("Eingabe ungültig")]
    InvalidInput,
    #[error("Port 143 (Klartext/STARTTLS) wird nicht unterstützt")]
    PlaintextPortRejected,
    #[error("Server nicht erreichbar")]
    Network,
    #[error("Zeitüberschreitung")]
    Timeout,
    #[error("Zertifikat nicht vertrauenswürdig")]
    TlsCertificateUntrusted,
    #[error("Zertifikat abgelaufen oder noch nicht gültig")]
    TlsCertificateExpired,
    #[error("Zertifikat passt nicht zum Servernamen")]
    TlsHostnameMismatch,
    #[error("Keine sichere TLS-Verbindung möglich")]
    TlsFailed,
    #[error("Anmeldung abgelehnt")]
    AuthenticationFailed,
    #[error("Unerwartete Antwort des Servers")]
    Protocol,
    #[error("Der Ordner wurde auf dem Server neu aufgebaut (UIDVALIDITY geändert)")]
    UidValidityChanged,
    #[error("Nachricht nicht mehr vorhanden")]
    MessageNotFound,
    #[error("Nachricht zu gross")]
    MessageTooLarge,
}

#[uniffi::export]
pub fn transport_version() -> String {
    env!("CARGO_PKG_VERSION").to_owned()
}

/// Lädt die Kopfdaten der neuesten `limit` Nachrichten des Posteingangs.
#[uniffi::export(async_runtime = "tokio")]
pub async fn fetch_inbox(
    account: ImapAccount,
    password: String,
    limit: u32,
) -> Result<InboxSnapshot, MailError> {
    let password = Zeroizing::new(password);
    imap::fetch_inbox(&account, &password, limit, &imap::Options::production()).await
}

/// Lädt eine Nachricht vollständig und liefert Text- und HTML-Teil.
/// `uid_validity` ist der Wert, unter dem die UID gespeichert wurde.
#[uniffi::export(async_runtime = "tokio")]
pub async fn fetch_body(
    account: ImapAccount,
    password: String,
    uid_validity: u32,
    uid: u32,
) -> Result<MailBody, MailError> {
    let password = Zeroizing::new(password);
    imap::fetch_body(
        &account,
        &password,
        uid_validity,
        uid,
        &imap::Options::production(),
    )
    .await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_exposed() {
        assert_eq!(transport_version(), "0.1.0");
    }
}
