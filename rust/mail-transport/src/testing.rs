//! Nur mit Feature `test-ca`: Zugänge für Integrationstests gegen einen lokalen
//! Testserver mit eigener Test-CA. App-Builds enthalten dieses Modul nicht.

use crate::{imap, ImapAccount, InboxSnapshot, MailBody, MailError};
use std::time::Duration;

pub use crate::imap::ImapSession;

#[derive(Debug, Clone)]
pub struct TestOptions {
    /// PEM der Test-CA; `None` verwendet ausschliesslich die produktiven Vertrauensanker.
    pub ca_pem: Option<Vec<u8>>,
    pub timeout: Duration,
    pub mailbox: String,
}

impl TestOptions {
    fn into_options(self) -> imap::Options {
        imap::Options {
            timeout: self.timeout,
            mailbox: self.mailbox,
            extra_roots_pem: self.ca_pem,
        }
    }
}

pub async fn fetch_inbox_with(
    account: &ImapAccount,
    password: &str,
    limit: u32,
    options: TestOptions,
) -> Result<InboxSnapshot, MailError> {
    imap::fetch_inbox(account, password, limit, &options.into_options()).await
}

pub async fn fetch_body_with(
    account: &ImapAccount,
    password: &str,
    uid_validity: u32,
    uid: u32,
    options: TestOptions,
) -> Result<MailBody, MailError> {
    imap::fetch_body(
        account,
        password,
        uid_validity,
        uid,
        &options.into_options(),
    )
    .await
}

/// Angemeldete Rohsitzung, um Testdaten anzulegen (APPEND, CREATE, DELETE).
pub async fn open_session(
    account: &ImapAccount,
    password: &str,
    options: TestOptions,
) -> Result<ImapSession, MailError> {
    imap::connect(account, password, &options.into_options()).await
}
