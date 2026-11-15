//! Integrationstests gegen GreenMail (siehe `tests/greenmail/` im Repo-Wurzelverzeichnis).
//!
//! Start: `tests/greenmail/run.sh`. Die Zertifikatsprüfung wird nirgends
//! abgeschaltet; der Testserver verwendet ein Zertifikat einer frisch erzeugten
//! Test-CA, die nur über das Feature `test-ca` als Vertrauensanker zugelassen wird.

use mail_transport::{
    testing::{fetch_body_with, fetch_inbox_with, open_session, TestOptions},
    ImapAccount, MailError,
};
use std::time::Duration;
use tokio::net::TcpListener;

// Zugangsdaten des lokalen Wegwerf-Testservers (tests/greenmail/compose.yaml).
const TEST_PASSWORD: &str = "nur-lokaler-test";

fn env_port(name: &str, default: u16) -> u16 {
    std::env::var(name)
        .ok()
        .and_then(|value| value.parse().ok())
        .unwrap_or(default)
}

fn imaps_port() -> u16 {
    env_port("GREENMAIL_IMAPS_PORT", 3993)
}

fn expired_port() -> u16 {
    env_port("GREENMAIL_EXPIRED_IMAPS_PORT", 4993)
}

fn plain_port() -> u16 {
    env_port("GREENMAIL_IMAP_PLAIN_PORT", 3143)
}

fn ca_pem() -> Vec<u8> {
    let path = std::env::var("GREENMAIL_CA_PEM")
        .unwrap_or_else(|_| "../../tests/greenmail/certs/ca.pem".to_owned());
    std::fs::read(&path).unwrap_or_else(|error| panic!("Test-CA {path} nicht lesbar: {error}"))
}

fn account(host: &str, port: u16, user: &str) -> ImapAccount {
    ImapAccount {
        host: host.to_owned(),
        port,
        username: user.to_owned(),
    }
}

fn trusted(mailbox: &str) -> TestOptions {
    TestOptions {
        ca_pem: Some(ca_pem()),
        timeout: Duration::from_secs(20),
        mailbox: mailbox.to_owned(),
    }
}

fn untrusted() -> TestOptions {
    TestOptions {
        ca_pem: None,
        timeout: Duration::from_secs(20),
        mailbox: "INBOX".to_owned(),
    }
}

async fn append(user: &str, mailbox: &str, message: &str) {
    let mut session = open_session(
        &account("localhost", imaps_port(), user),
        TEST_PASSWORD,
        trusted("INBOX"),
    )
    .await
    .expect("Sitzung zum Anlegen von Testdaten");
    session
        .append(mailbox, None, None, message.as_bytes())
        .await
        .expect("APPEND");
    let _ = session.logout().await;
}

fn plain_message(subject: &str, from: &str, text: &str) -> String {
    format!(
        "From: {from}\r\nTo: test@example.org\r\nSubject: {subject}\r\nMessage-ID: <{}@example.org>\r\nDate: Sun, 15 Nov 2026 10:00:00 +0100\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n{text}\r\n",
        subject.len() + text.len()
    )
}

#[tokio::test]
async fn verbindung_login_list_uid_fetch_logout_und_wiederverbindung() {
    let user = "liste";
    append(
        user,
        "INBOX",
        &plain_message("Erste", "a@example.org", "eins"),
    )
    .await;
    append(
        user,
        "INBOX",
        &plain_message(
            "=?UTF-8?Q?Gr=C3=BC=C3=9Fe_aus_Z=C3=BCrich?=",
            "=?UTF-8?Q?J=C3=BCrg_M=C3=BCller?= <juerg@example.org>",
            "zwei",
        ),
    )
    .await;
    append(
        user,
        "INBOX",
        &plain_message("Dritte", "c@example.org", "drei"),
    )
    .await;

    let acc = account("localhost", imaps_port(), user);
    let snapshot = fetch_inbox_with(&acc, TEST_PASSWORD, 50, trusted("INBOX"))
        .await
        .expect("erster Abruf");

    assert!(snapshot.uid_validity > 0);
    assert!(snapshot
        .folders
        .iter()
        .any(|name| name.eq_ignore_ascii_case("INBOX")));
    assert_eq!(snapshot.headers.len(), 3);
    // Neueste zuerst, UIDs streng absteigend.
    assert!(snapshot
        .headers
        .windows(2)
        .all(|pair| pair[0].uid > pair[1].uid));
    assert_eq!(snapshot.headers[0].subject.as_deref(), Some("Dritte"));
    let middle = &snapshot.headers[1];
    assert_eq!(middle.subject.as_deref(), Some("Grüße aus Zürich"));
    assert_eq!(middle.sender_name.as_deref(), Some("Jürg Müller"));
    assert_eq!(middle.sender_address.as_deref(), Some("juerg@example.org"));
    assert!(middle.internal_date_epoch_seconds > 0);
    assert!(middle.message_id.is_some());
    assert!(
        !middle.seen,
        "EXAMINE/PEEK darf nichts als gelesen markieren"
    );

    // Wiederverbindung liefert dasselbe Bild (gleiche UIDVALIDITY, gleiche UIDs).
    let again = fetch_inbox_with(&acc, TEST_PASSWORD, 50, trusted("INBOX"))
        .await
        .expect("zweiter Abruf");
    assert_eq!(again, snapshot);

    // Begrenzung liefert die neuesten Nachrichten.
    let limited = fetch_inbox_with(&acc, TEST_PASSWORD, 2, trusted("INBOX"))
        .await
        .expect("begrenzter Abruf");
    assert_eq!(limited.headers.len(), 2);
    assert_eq!(limited.headers[0].uid, snapshot.headers[0].uid);
    assert_eq!(limited.headers[1].uid, snapshot.headers[1].uid);
}

#[tokio::test]
async fn leerer_posteingang() {
    let acc = account("localhost", imaps_port(), "leer");
    let snapshot = fetch_inbox_with(&acc, TEST_PASSWORD, 50, trusted("INBOX"))
        .await
        .expect("Abruf");
    assert!(snapshot.headers.is_empty());
}

#[tokio::test]
async fn mail_oeffnen_liefert_text_und_html() {
    let user = "inhalt";
    let multipart = concat!(
        "From: Shop <shop@example.org>\r\n",
        "To: test@example.org\r\n",
        "Subject: Multipart\r\n",
        "MIME-Version: 1.0\r\n",
        "Content-Type: multipart/alternative; boundary=\"grenze\"\r\n",
        "\r\n",
        "--grenze\r\n",
        "Content-Type: text/plain; charset=utf-8\r\n",
        "Content-Transfer-Encoding: quoted-printable\r\n",
        "\r\n",
        "Gr=C3=BCezi als Text\r\n",
        "--grenze\r\n",
        "Content-Type: text/html; charset=utf-8\r\n",
        "Content-Transfer-Encoding: quoted-printable\r\n",
        "\r\n",
        "<p>Gr=C3=BCezi als <b>HTML</b></p><img src=3D\"https://tracker.example/p.gif\">\r\n",
        "--grenze--\r\n"
    );
    append(
        user,
        "INBOX",
        &plain_message("Nur Text", "a@example.org", "Hallo Welt"),
    )
    .await;
    append(user, "INBOX", multipart).await;

    let acc = account("localhost", imaps_port(), user);
    let snapshot = fetch_inbox_with(&acc, TEST_PASSWORD, 50, trusted("INBOX"))
        .await
        .expect("Abruf");
    let multipart_uid = snapshot.headers[0].uid;
    let plain_uid = snapshot.headers[1].uid;

    let body = fetch_body_with(
        &acc,
        TEST_PASSWORD,
        snapshot.uid_validity,
        multipart_uid,
        trusted("INBOX"),
    )
    .await
    .expect("Multipart-Inhalt");
    assert_eq!(body.text.as_deref().map(str::trim), Some("Grüezi als Text"));
    assert!(body
        .html
        .as_deref()
        .unwrap_or_default()
        .contains("<p>Grüezi als <b>HTML</b></p>"));

    let body = fetch_body_with(
        &acc,
        TEST_PASSWORD,
        snapshot.uid_validity,
        plain_uid,
        trusted("INBOX"),
    )
    .await
    .expect("Text-Inhalt");
    assert_eq!(body.text.as_deref().map(str::trim), Some("Hallo Welt"));
    assert_eq!(body.html, None);

    // Lesen darf den Gelesen-Status nicht verändern (BODY.PEEK + EXAMINE).
    let after = fetch_inbox_with(&acc, TEST_PASSWORD, 50, trusted("INBOX"))
        .await
        .expect("Abruf danach");
    assert!(after.headers.iter().all(|header| !header.seen));

    let missing = fetch_body_with(
        &acc,
        TEST_PASSWORD,
        snapshot.uid_validity,
        multipart_uid + 1000,
        trusted("INBOX"),
    )
    .await;
    assert_eq!(missing, Err(MailError::MessageNotFound));
}

#[tokio::test]
async fn negativ_falsche_zugangsdaten() {
    let acc = account("localhost", imaps_port(), "liste");
    let result = fetch_inbox_with(&acc, "falsches-passwort", 10, trusted("INBOX")).await;
    assert_eq!(result, Err(MailError::AuthenticationFailed));
}

#[tokio::test]
async fn negativ_falscher_hostname() {
    // Das Serverzertifikat gilt nur für „localhost“, nicht für die IP-Adresse.
    let acc = account("127.0.0.1", imaps_port(), "liste");
    let result = fetch_inbox_with(&acc, TEST_PASSWORD, 10, trusted("INBOX")).await;
    assert_eq!(result, Err(MailError::TlsHostnameMismatch));
}

#[tokio::test]
async fn negativ_unbekannte_zertifizierungsstelle_ueber_die_produktive_schnittstelle() {
    // Genau die Funktion, die die Apps aufrufen: ohne Test-CA muss sie ablehnen.
    let acc = account("localhost", imaps_port(), "liste");
    let result = mail_transport::fetch_inbox(acc.clone(), TEST_PASSWORD.to_owned(), 10).await;
    assert_eq!(result, Err(MailError::TlsCertificateUntrusted));

    let result = fetch_inbox_with(&acc, TEST_PASSWORD, 10, untrusted()).await;
    assert_eq!(result, Err(MailError::TlsCertificateUntrusted));
}

#[tokio::test]
async fn negativ_abgelaufenes_zertifikat() {
    let acc = account("localhost", expired_port(), "liste");
    let result = fetch_inbox_with(&acc, TEST_PASSWORD, 10, trusted("INBOX")).await;
    assert_eq!(result, Err(MailError::TlsCertificateExpired));
}

#[tokio::test]
async fn negativ_kein_klartext_fallback() {
    // Der Klartext-IMAP-Port des Testservers: Der Client muss am TLS-Handshake
    // scheitern und darf sich nicht im Klartext anmelden.
    let acc = account("localhost", plain_port(), "liste");
    let result = fetch_inbox_with(&acc, TEST_PASSWORD, 10, trusted("INBOX")).await;
    assert!(
        matches!(result, Err(MailError::TlsFailed) | Err(MailError::Network)),
        "unerwartet: {result:?}"
    );
}

#[tokio::test]
async fn negativ_timeout() {
    // Server nimmt die Verbindung an und schweigt.
    let listener = TcpListener::bind("127.0.0.1:0").await.expect("bind");
    let port = listener.local_addr().expect("addr").port();
    let silent = tokio::spawn(async move {
        let mut held = Vec::new();
        loop {
            if let Ok((socket, _)) = listener.accept().await {
                held.push(socket);
            }
        }
    });
    let options = TestOptions {
        ca_pem: Some(ca_pem()),
        timeout: Duration::from_secs(2),
        mailbox: "INBOX".to_owned(),
    };
    let result = fetch_inbox_with(
        &account("localhost", port, "liste"),
        TEST_PASSWORD,
        10,
        options,
    )
    .await;
    silent.abort();
    assert_eq!(result, Err(MailError::Timeout));
}

#[tokio::test]
async fn negativ_verbindungsabbruch() {
    // Server nimmt die Verbindung an und schliesst sie sofort.
    let listener = TcpListener::bind("127.0.0.1:0").await.expect("bind");
    let port = listener.local_addr().expect("addr").port();
    let closer = tokio::spawn(async move {
        loop {
            if let Ok((socket, _)) = listener.accept().await {
                drop(socket);
            }
        }
    });
    let result = fetch_inbox_with(
        &account("localhost", port, "liste"),
        TEST_PASSWORD,
        10,
        trusted("INBOX"),
    )
    .await;
    closer.abort();
    assert!(
        matches!(result, Err(MailError::Network) | Err(MailError::TlsFailed)),
        "unerwartet: {result:?}"
    );
}

#[tokio::test]
async fn negativ_uidvalidity_wechsel() {
    let user = "wechsel";
    let acc = account("localhost", imaps_port(), user);
    let folder = "Wechsel";

    let mut session = open_session(&acc, TEST_PASSWORD, trusted("INBOX"))
        .await
        .expect("Sitzung");
    session.create(folder).await.expect("CREATE");
    let _ = session.logout().await;
    append(user, folder, &plain_message("Alt", "a@example.org", "alt")).await;

    let before = fetch_inbox_with(&acc, TEST_PASSWORD, 10, trusted(folder))
        .await
        .expect("Abruf vor dem Wechsel");
    assert_eq!(before.headers.len(), 1);
    let old_uid = before.headers[0].uid;

    // Ordner löschen und neu anlegen: Der Server muss eine neue UIDVALIDITY vergeben.
    let mut session = open_session(&acc, TEST_PASSWORD, trusted("INBOX"))
        .await
        .expect("Sitzung");
    session.delete(folder).await.expect("DELETE");
    tokio::time::sleep(Duration::from_millis(1100)).await;
    session.create(folder).await.expect("CREATE");
    let _ = session.logout().await;
    append(user, folder, &plain_message("Neu", "b@example.org", "neu")).await;

    let after = fetch_inbox_with(&acc, TEST_PASSWORD, 10, trusted(folder))
        .await
        .expect("Abruf nach dem Wechsel");
    assert_ne!(
        after.uid_validity, before.uid_validity,
        "Testserver hat die UIDVALIDITY nicht gewechselt"
    );
    assert_eq!(after.headers[0].subject.as_deref(), Some("Neu"));

    // Ein Abruf mit der alten UIDVALIDITY darf keine (falsche) Nachricht liefern.
    let stale = fetch_body_with(
        &acc,
        TEST_PASSWORD,
        before.uid_validity,
        old_uid,
        trusted(folder),
    )
    .await;
    assert_eq!(stale, Err(MailError::UidValidityChanged));
}

/// Belegt Review-Befund B4: `Fetch::header()` liefert für
/// `BODY[HEADER.FIELDS (...)]` nichts – der ursprüngliche Abruf hätte leere
/// Absender und Betreffs ergeben.
#[tokio::test]
async fn review_b4_header_fields_sind_ueber_header_nicht_lesbar() {
    use futures_util::TryStreamExt;
    let user = "b4";
    append(
        user,
        "INBOX",
        &plain_message("Sichtbar?", "a@example.org", "x"),
    )
    .await;
    let mut session = open_session(
        &account("localhost", imaps_port(), user),
        TEST_PASSWORD,
        trusted("INBOX"),
    )
    .await
    .expect("Sitzung");
    session.examine("INBOX").await.expect("EXAMINE");
    let fetched: Vec<_> = session
        .fetch("1:*", "(UID BODY.PEEK[HEADER.FIELDS (FROM SUBJECT)])")
        .await
        .expect("FETCH")
        .try_collect()
        .await
        .expect("FETCH-Antworten");
    assert!(!fetched.is_empty());
    assert!(
        fetched.iter().all(|message| message.header().is_none()),
        "B4 widerlegt: header() liefert HEADER.FIELDS doch"
    );
    let _ = session.logout().await;
}
