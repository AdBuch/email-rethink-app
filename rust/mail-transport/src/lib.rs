//! Gemeinsamer IMAPS-Transportkern. Keine Klartextverbindung oder TLS-Ausnahme.
//! Der synchrone UniFFI-Einstieg muss auf einem Hintergrundthread aufgerufen werden.

use futures_util::TryStreamExt;
use std::{sync::Arc, time::Duration};
use tokio::{net::TcpStream, time::timeout};
use tokio_rustls::{
    rustls::{pki_types::ServerName, ClientConfig, RootCertStore},
    TlsConnector,
};

uniffi::include_scaffolding!("mail_transport");

const TIMEOUT: Duration = Duration::from_secs(20);
const MAX_HEADERS: u32 = 100;

#[derive(Debug)]
pub enum MailError {
    InvalidInput,
    ConnectionFailed,
    TlsFailed,
    AuthenticationFailed,
    ProtocolFailed,
}

pub struct MailFolder {
    pub name: String,
}

pub struct MailHeader {
    pub uid: u32,
    pub sender: String,
    pub subject: String,
}

pub struct InboxSnapshot {
    pub folders: Vec<MailFolder>,
    pub headers: Vec<MailHeader>,
}

pub fn transport_version() -> String {
    env!("CARGO_PKG_VERSION").to_owned()
}

pub fn is_valid_imaps_port(port: u16) -> bool {
    port == 993
}

fn validate(host: &str, port: u16, username: &str, limit: u32) -> Result<(), MailError> {
    if host.is_empty()
        || host.len() > 253
        || host.chars().any(|c| c.is_whitespace() || c.is_control())
        || username.trim().is_empty()
        || !is_valid_imaps_port(port)
        || limit == 0
        || limit > MAX_HEADERS
    {
        return Err(MailError::InvalidInput);
    }
    Ok(())
}

/// Einmaliger Inbox-Snapshot für den ersten Entwicklungsschnitt.
/// Zugangsdaten werden nicht persistiert oder geloggt.
pub fn fetch_inbox(
    host: String,
    port: u16,
    username: String,
    password: String,
    limit: u32,
) -> Result<InboxSnapshot, MailError> {
    validate(&host, port, &username, limit)?;
    if password.is_empty() {
        return Err(MailError::InvalidInput);
    }
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .map_err(|_| MailError::ConnectionFailed)?;
    runtime.block_on(async {
        timeout(TIMEOUT, fetch_inbox_async(&host, port, &username, &password, limit))
            .await
            .map_err(|_| MailError::ConnectionFailed)?
    })
}

async fn fetch_inbox_async(
    host: &str,
    port: u16,
    username: &str,
    password: &str,
    limit: u32,
) -> Result<InboxSnapshot, MailError> {
    let tcp = TcpStream::connect((host, port))
        .await
        .map_err(|_| MailError::ConnectionFailed)?;

    // WebPKI-Roots mit obligatorischer Hostname- und Zertifikatsprüfung.
    // Kein eigener CertificateVerifier, keine Möglichkeit zum Deaktivieren.
    let roots = RootCertStore {
        roots: webpki_roots::TLS_SERVER_ROOTS.to_vec(),
    };
    let config = ClientConfig::builder()
        .with_root_certificates(roots)
        .with_no_client_auth();
    let server_name =
        ServerName::try_from(host.to_owned()).map_err(|_| MailError::InvalidInput)?;
    let tls = TlsConnector::from(Arc::new(config))
        .connect(server_name, tcp)
        .await
        .map_err(|_| MailError::TlsFailed)?;

    let mut client = async_imap::Client::new(tls);
    client
        .read_response()
        .await
        .map_err(|_| MailError::ProtocolFailed)?
        .ok_or(MailError::ProtocolFailed)?;

    let mut session = client
        .login(username, password)
        .await
        .map_err(|_| MailError::AuthenticationFailed)?;

    let folders_stream = session
        .list(None, Some("*"))
        .await
        .map_err(|_| MailError::ProtocolFailed)?;
    let folders = folders_stream
        .map_ok(|folder| MailFolder {
            name: folder.name().to_owned(),
        })
        .try_collect::<Vec<_>>()
        .await
        .map_err(|_| MailError::ProtocolFailed)?;

    let mailbox = session
        .examine("INBOX")
        .await
        .map_err(|_| MailError::ProtocolFailed)?;
    let mut headers = Vec::new();
    if mailbox.exists > 0 {
        // Begrenzter Fetch über Sequenznummern, UID wird im Ergebnis gespeichert.
        let first = mailbox.exists.saturating_sub(limit).saturating_add(1);
        let range = format!("{first}:{}", mailbox.exists);
        let fetched = session
            .fetch(range, "(UID BODY.PEEK[HEADER.FIELDS (FROM SUBJECT)])")
            .await
            .map_err(|_| MailError::ProtocolFailed)?;
        let messages = fetched
            .try_collect::<Vec<_>>()
            .await
            .map_err(|_| MailError::ProtocolFailed)?;
        for message in messages {
            if let Some(uid) = message.uid {
                let raw = message.header().unwrap_or_default();
                let (sender, subject) = parse_basic_headers(raw);
                headers.push(MailHeader { uid, sender, subject });
            }
        }
    }
    session.logout().await.map_err(|_| MailError::ProtocolFailed)?;
    Ok(InboxSnapshot { folders, headers })
}

/// Vorläufige reine Header-Anzeige. RFC-2047-MIME-Decodierung folgt in core-mime.
fn parse_basic_headers(bytes: &[u8]) -> (String, String) {
    let raw = String::from_utf8_lossy(bytes);
    let mut sender = String::new();
    let mut subject = String::new();
    for line in raw.lines() {
        if let Some(value) = line.strip_prefix("From:").or_else(|| line.strip_prefix("from:")) {
            sender = value.trim().to_owned();
        }
        if let Some(value) = line.strip_prefix("Subject:").or_else(|| line.strip_prefix("subject:")) {
            subject = value.trim().to_owned();
        }
    }
    (sender, subject)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_exposed() {
        assert_eq!(transport_version(), "0.1.0");
    }

    #[test]
    fn only_implicit_tls_port_is_accepted() {
        assert!(is_valid_imaps_port(993));
        assert!(!is_valid_imaps_port(143));
        assert!(!is_valid_imaps_port(0));
    }

    #[test]
    fn invalid_parameters_rejected_before_network() {
        assert!(matches!(validate("imap.example.org", 143, "a", 10), Err(MailError::InvalidInput)));
        assert!(matches!(validate("imap.example.org", 993, "a", 101), Err(MailError::InvalidInput)));
        assert!(matches!(validate("", 993, "a", 10), Err(MailError::InvalidInput)));
        assert!(matches!(validate("imap.example.org", 993, "a", 10), Ok(())));
    }

    #[test]
    fn parses_basic_headers() {
        let (from, subject) = parse_basic_headers(b"From: test@example.org\r\nSubject: Hallo\r\n");
        assert_eq!(from, "test@example.org");
        assert_eq!(subject, "Hallo");
    }
}
