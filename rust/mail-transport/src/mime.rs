//! MIME-Dekodierung über `mail-parser` (kein eigener Parser).

use crate::MailBody;
use mail_parser::MessageParser;

/// Dekodiert einen unstrukturierten Kopfzeilenwert aus dem IMAP-ENVELOPE
/// (RFC 2047 „encoded words“, z. B. `=?UTF-8?Q?Gr=C3=BC=C3=9Fe?=`).
pub(crate) fn decode_header_text(raw: &[u8]) -> String {
    let mut pseudo_message = Vec::with_capacity(raw.len() + 16);
    pseudo_message.extend_from_slice(b"Subject: ");
    // Zeilenumbrüche würden weitere Kopfzeilen einschleusen.
    pseudo_message.extend(raw.iter().map(|byte| match byte {
        b'\r' | b'\n' => b' ',
        other => *other,
    }));
    pseudo_message.extend_from_slice(b"\r\n\r\n");

    MessageParser::default()
        .parse(&pseudo_message)
        .and_then(|message| message.subject().map(|subject| subject.trim().to_owned()))
        .unwrap_or_else(|| String::from_utf8_lossy(raw).trim().to_owned())
}

/// Liefert den Textteil und – falls die Nachricht wirklich einen HTML-Teil
/// enthält – das HTML. Fehlt ein Textteil, erzeugt `mail-parser` ihn aus dem HTML.
pub(crate) fn extract_body(raw: &[u8]) -> MailBody {
    let Some(message) = MessageParser::default().parse(raw) else {
        return MailBody {
            text: Some(String::from_utf8_lossy(raw).into_owned()),
            html: None,
        };
    };
    let text = message
        .body_text(0)
        .map(|text| text.into_owned())
        .filter(|text| !text.trim().is_empty());
    let html = if declares_html_part(raw) {
        message
            .body_html(0)
            .map(|html| html.into_owned())
            .filter(|html| !html.trim().is_empty())
    } else {
        None
    };
    MailBody { text, html }
}

fn declares_html_part(raw: &[u8]) -> bool {
    const NEEDLE: &[u8] = b"text/html";
    raw.windows(NEEDLE.len())
        .any(|window| window.eq_ignore_ascii_case(NEEDLE))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_encoded_words() {
        assert_eq!(
            decode_header_text(b"=?UTF-8?Q?Gr=C3=BC=C3=9Fe_aus_Z=C3=BCrich?="),
            "Grüße aus Zürich"
        );
        assert_eq!(
            decode_header_text(b"=?ISO-8859-1?B?SvxyZyBN/GxsZXI=?="),
            "Jürg Müller"
        );
        assert_eq!(decode_header_text(b"Plain subject"), "Plain subject");
    }

    #[test]
    fn header_injection_is_neutralised() {
        let decoded = decode_header_text(b"Hallo\r\nX-Evil: 1");
        assert!(decoded.starts_with("Hallo"));
        assert!(!decoded.contains('\n'));
    }

    #[test]
    fn plain_text_message_has_no_html() {
        let raw = b"From: a@example.org\r\nSubject: Test\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nHallo Welt\r\n";
        let body = extract_body(raw);
        assert_eq!(body.text.as_deref().map(str::trim), Some("Hallo Welt"));
        assert_eq!(body.html, None);
    }

    #[test]
    fn multipart_alternative_yields_both_parts() {
        let raw = concat!(
            "From: a@example.org\r\n",
            "Subject: Test\r\n",
            "MIME-Version: 1.0\r\n",
            "Content-Type: multipart/alternative; boundary=\"b\"\r\n",
            "\r\n",
            "--b\r\n",
            "Content-Type: text/plain; charset=utf-8\r\n",
            "Content-Transfer-Encoding: quoted-printable\r\n",
            "\r\n",
            "Gr=C3=BCezi\r\n",
            "--b\r\n",
            "Content-Type: text/html; charset=utf-8\r\n",
            "\r\n",
            "<p>Gr\u{fc}ezi <script>alert(1)</script></p>\r\n",
            "--b--\r\n"
        );
        let body = extract_body(raw.as_bytes());
        assert_eq!(body.text.as_deref().map(str::trim), Some("Grüezi"));
        assert!(body
            .html
            .as_deref()
            .unwrap_or_default()
            .contains("<p>Grüezi"));
    }

    #[test]
    fn html_only_message_gets_a_text_fallback() {
        let raw = b"From: a@example.org\r\nSubject: Test\r\nContent-Type: text/html; charset=utf-8\r\n\r\n<p>Nur HTML</p>\r\n";
        let body = extract_body(raw);
        assert!(body
            .text
            .as_deref()
            .unwrap_or_default()
            .contains("Nur HTML"));
        assert!(body
            .html
            .as_deref()
            .unwrap_or_default()
            .contains("<p>Nur HTML</p>"));
    }
}
