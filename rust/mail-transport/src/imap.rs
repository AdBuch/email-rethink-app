//! IMAP-Ablauf: verbinden, anmelden, Kopfdaten und Inhalte per UID lesen.

use crate::{
    mime, tls, ImapAccount, InboxSnapshot, MailBody, MailError, MailHeader, MAX_BODY_BYTES,
    MAX_HEADERS,
};
use async_imap::{
    error::Error as ImapError,
    types::{Fetch, Flag},
    Session,
};
use futures_util::TryStreamExt;
use std::{future::Future, time::Duration};
use tokio::net::TcpStream;
use tokio_rustls::{client::TlsStream, rustls::pki_types::ServerName};

pub(crate) type ImapSession = Session<TlsStream<TcpStream>>;

const HEADER_QUERY: &str = "(UID FLAGS INTERNALDATE ENVELOPE)";

/// Interne Stellschrauben. Die Apps verwenden immer [`Options::production`].
#[derive(Debug, Clone)]
pub(crate) struct Options {
    pub timeout: Duration,
    pub mailbox: String,
    #[cfg(feature = "test-ca")]
    pub extra_roots_pem: Option<Vec<u8>>,
}

impl Options {
    pub(crate) fn production() -> Self {
        Options {
            timeout: Duration::from_secs(45),
            mailbox: "INBOX".to_owned(),
            #[cfg(feature = "test-ca")]
            extra_roots_pem: None,
        }
    }
}

pub(crate) fn validate(account: &ImapAccount, password: &str) -> Result<(), MailError> {
    let host = account.host.as_str();
    if host.is_empty()
        || host.len() > 253
        || host
            .chars()
            .any(|c| c.is_whitespace() || c.is_control() || c == '/' || c == '@')
        || account.username.trim().is_empty()
        || account.username.chars().any(|c| c.is_control())
        || password.is_empty()
        || password.chars().any(|c| c == '\r' || c == '\n')
        || account.port == 0
    {
        return Err(MailError::InvalidInput);
    }
    // Implizites TLS ist auf jedem Port erlaubt; die Sicherheit kommt aus dem
    // TLS-Handshake. Port 143 ist der Klartext-/STARTTLS-Port und wird abgelehnt,
    // bevor irgendetwas gesendet wird.
    if account.port == 143 {
        return Err(MailError::PlaintextPortRejected);
    }
    Ok(())
}

async fn with_timeout<T>(
    limit: Duration,
    work: impl Future<Output = Result<T, MailError>>,
) -> Result<T, MailError> {
    tokio::time::timeout(limit, work)
        .await
        .unwrap_or(Err(MailError::Timeout))
}

fn map_imap(error: ImapError) -> MailError {
    match error {
        ImapError::Io(_) | ImapError::ConnectionLost => MailError::Network,
        _ => MailError::Protocol,
    }
}

/// TCP → TLS (mit Prüfung) → Begrüssung → LOGIN. Vor dem erfolgreichen
/// TLS-Handshake wird kein einziges IMAP-Byte gesendet.
pub(crate) async fn connect(
    account: &ImapAccount,
    password: &str,
    options: &Options,
) -> Result<ImapSession, MailError> {
    let connector = tls::connector(options)?;
    let server_name =
        ServerName::try_from(account.host.clone()).map_err(|_| MailError::InvalidInput)?;
    let tcp = TcpStream::connect((account.host.as_str(), account.port))
        .await
        .map_err(|_| MailError::Network)?;
    let stream = connector
        .connect(server_name, tcp)
        .await
        .map_err(|error| tls::classify(&error))?;

    let mut client = async_imap::Client::new(stream);
    match client.read_response().await {
        Ok(Some(_greeting)) => {}
        Ok(None) | Err(_) => return Err(MailError::Network),
    }
    client
        .login(&account.username, password)
        .await
        .map_err(|(error, _client)| match error {
            ImapError::No(_) | ImapError::Bad(_) => MailError::AuthenticationFailed,
            other => map_imap(other),
        })
}

pub(crate) async fn fetch_inbox(
    account: &ImapAccount,
    password: &str,
    limit: u32,
    options: &Options,
) -> Result<InboxSnapshot, MailError> {
    validate(account, password)?;
    if limit == 0 || limit > MAX_HEADERS {
        return Err(MailError::InvalidInput);
    }
    with_timeout(options.timeout, async {
        let mut session = connect(account, password, options).await?;
        let result = read_snapshot(&mut session, &options.mailbox, limit).await;
        // Ein fehlgeschlagenes LOGOUT darf bereits geladene Daten nicht verwerfen.
        let _ = session.logout().await;
        result
    })
    .await
}

pub(crate) async fn fetch_body(
    account: &ImapAccount,
    password: &str,
    uid_validity: u32,
    uid: u32,
    options: &Options,
) -> Result<MailBody, MailError> {
    validate(account, password)?;
    if uid == 0 {
        return Err(MailError::InvalidInput);
    }
    with_timeout(options.timeout, async {
        let mut session = connect(account, password, options).await?;
        let result = read_body(&mut session, &options.mailbox, uid_validity, uid).await;
        let _ = session.logout().await;
        result
    })
    .await
}

async fn read_snapshot(
    session: &mut ImapSession,
    mailbox_name: &str,
    limit: u32,
) -> Result<InboxSnapshot, MailError> {
    let folders = {
        let names = session.list(Some(""), Some("*")).await.map_err(map_imap)?;
        names
            .map_ok(|name| name.name().to_owned())
            .try_collect::<Vec<_>>()
            .await
            .map_err(map_imap)?
    };

    let mailbox = session.examine(mailbox_name).await.map_err(map_imap)?;
    let uid_validity = mailbox.uid_validity.ok_or(MailError::Protocol)?;

    let mut headers = Vec::new();
    if mailbox.exists > 0 {
        if let Some((lowest, highest)) = newest_uid_range(session, mailbox.exists, limit).await? {
            let fetched = {
                let stream = session
                    .uid_fetch(format!("{lowest}:{highest}"), HEADER_QUERY)
                    .await
                    .map_err(map_imap)?;
                stream.try_collect::<Vec<_>>().await.map_err(map_imap)?
            };
            headers = fetched.iter().filter_map(header_from_fetch).collect();
            headers.sort_by_key(|a| std::cmp::Reverse(a.uid));
            headers.truncate(limit as usize);
        }
    }

    Ok(InboxSnapshot {
        uid_validity,
        folders,
        headers,
    })
}

/// Ermittelt den UID-Bereich der neuesten `limit` Nachrichten.
///
/// Sequenznummern dienen nur als Suchschlüssel („die letzten n“); gespeichert
/// und abgerufen wird ausschliesslich über UIDs.
async fn newest_uid_range(
    session: &mut ImapSession,
    exists: u32,
    limit: u32,
) -> Result<Option<(u32, u32)>, MailError> {
    let first = exists.saturating_sub(limit).saturating_add(1);
    let uids = match session.uid_search(format!("{first}:{exists}")).await {
        Ok(uids) => uids,
        // Server ohne Sequenzbereich als Suchschlüssel: alle UIDs holen.
        Err(ImapError::No(_)) | Err(ImapError::Bad(_)) => {
            session.uid_search("ALL").await.map_err(map_imap)?
        }
        Err(other) => return Err(map_imap(other)),
    };
    let mut sorted: Vec<u32> = uids.into_iter().collect();
    sorted.sort_unstable();
    let newest = &sorted[sorted.len().saturating_sub(limit as usize)..];
    Ok(match (newest.first(), newest.last()) {
        (Some(lowest), Some(highest)) => Some((*lowest, *highest)),
        _ => None,
    })
}

fn header_from_fetch(fetch: &Fetch) -> Option<MailHeader> {
    let uid = fetch.uid?;
    let envelope = fetch.envelope();
    let author = envelope
        .and_then(|envelope| envelope.from.as_ref())
        .and_then(|addresses| addresses.first());
    let sender_address = author.and_then(|address| {
        let mailbox = address.mailbox.as_deref()?;
        let mailbox = String::from_utf8_lossy(mailbox);
        Some(match address.host.as_deref() {
            Some(host) => format!("{mailbox}@{}", String::from_utf8_lossy(host)),
            None => mailbox.into_owned(),
        })
    });
    Some(MailHeader {
        uid,
        sender_name: author
            .and_then(|address| address.name.as_deref())
            .map(mime::decode_header_text)
            .filter(|name| !name.is_empty()),
        sender_address,
        subject: envelope
            .and_then(|envelope| envelope.subject.as_deref())
            .map(mime::decode_header_text),
        internal_date_epoch_seconds: fetch
            .internal_date()
            .map(|date| date.timestamp())
            .unwrap_or(0),
        message_id: envelope
            .and_then(|envelope| envelope.message_id.as_deref())
            .map(|id| String::from_utf8_lossy(id).into_owned()),
        seen: fetch.flags().any(|flag| matches!(flag, Flag::Seen)),
    })
}

async fn read_body(
    session: &mut ImapSession,
    mailbox_name: &str,
    expected_uid_validity: u32,
    uid: u32,
) -> Result<MailBody, MailError> {
    let mailbox = session.examine(mailbox_name).await.map_err(map_imap)?;
    if mailbox.uid_validity != Some(expected_uid_validity) {
        return Err(MailError::UidValidityChanged);
    }

    let sizes = collect_uid_fetch(session, uid, "(UID RFC822.SIZE)").await?;
    let size = sizes
        .iter()
        .find(|fetch| fetch.uid == Some(uid))
        .ok_or(MailError::MessageNotFound)?
        .size
        .unwrap_or(0);
    if size > MAX_BODY_BYTES {
        return Err(MailError::MessageTooLarge);
    }

    let messages = collect_uid_fetch(session, uid, "(UID BODY.PEEK[])").await?;
    let message = messages
        .iter()
        .find(|fetch| fetch.uid == Some(uid))
        .ok_or(MailError::MessageNotFound)?;
    let raw = message.body().ok_or(MailError::Protocol)?;
    Ok(mime::extract_body(raw))
}

async fn collect_uid_fetch(
    session: &mut ImapSession,
    uid: u32,
    query: &str,
) -> Result<Vec<Fetch>, MailError> {
    let stream = session
        .uid_fetch(uid.to_string(), query)
        .await
        .map_err(map_imap)?;
    stream.try_collect::<Vec<_>>().await.map_err(map_imap)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn account(host: &str, port: u16, username: &str) -> ImapAccount {
        ImapAccount {
            host: host.to_owned(),
            port,
            username: username.to_owned(),
        }
    }

    #[test]
    fn implicit_tls_is_allowed_on_any_port_except_the_plaintext_port() {
        assert_eq!(
            validate(&account("imap.example.org", 993, "a"), "pw"),
            Ok(())
        );
        assert_eq!(validate(&account("localhost", 3993, "a"), "pw"), Ok(()));
        assert_eq!(
            validate(&account("imap.example.org", 143, "a"), "pw"),
            Err(MailError::PlaintextPortRejected)
        );
        assert_eq!(
            validate(&account("imap.example.org", 0, "a"), "pw"),
            Err(MailError::InvalidInput)
        );
    }

    #[test]
    fn malformed_input_is_rejected_before_any_network_use() {
        assert_eq!(
            validate(&account("", 993, "a"), "pw"),
            Err(MailError::InvalidInput)
        );
        assert_eq!(
            validate(&account("imap example.org", 993, "a"), "pw"),
            Err(MailError::InvalidInput)
        );
        assert_eq!(
            validate(&account("imap.example.org", 993, " "), "pw"),
            Err(MailError::InvalidInput)
        );
        assert_eq!(
            validate(&account("imap.example.org", 993, "a"), ""),
            Err(MailError::InvalidInput)
        );
        assert_eq!(
            validate(&account("imap.example.org", 993, "a"), "pw\r\nA1 LOGOUT"),
            Err(MailError::InvalidInput)
        );
    }

    #[tokio::test]
    async fn plaintext_port_fails_without_connecting() {
        let result = fetch_inbox(
            &account("imap.example.invalid", 143, "a"),
            "pw",
            10,
            &Options::production(),
        )
        .await;
        assert_eq!(result, Err(MailError::PlaintextPortRejected));
    }

    #[tokio::test]
    async fn limit_is_bounded() {
        let acc = account("imap.example.invalid", 993, "a");
        let options = Options::production();
        assert_eq!(
            fetch_inbox(&acc, "pw", 0, &options).await,
            Err(MailError::InvalidInput)
        );
        assert_eq!(
            fetch_inbox(&acc, "pw", MAX_HEADERS + 1, &options).await,
            Err(MailError::InvalidInput)
        );
    }
}
