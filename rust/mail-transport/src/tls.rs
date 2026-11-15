//! TLS-Konfiguration: Mozilla-Vertrauensanker, Pflichtprüfung von Kette und Hostname.

use crate::{imap::Options, MailError};
use std::{io, sync::Arc};
use tokio_rustls::{
    rustls::{self, CertificateError, ClientConfig, RootCertStore},
    TlsConnector,
};

pub(crate) fn connector(options: &Options) -> Result<TlsConnector, MailError> {
    let mut roots = RootCertStore::empty();
    roots.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    add_test_roots(&mut roots, options)?;

    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let config = ClientConfig::builder_with_provider(provider)
        .with_safe_default_protocol_versions()
        .map_err(|_| MailError::TlsFailed)?
        .with_root_certificates(roots)
        .with_no_client_auth();
    Ok(TlsConnector::from(Arc::new(config)))
}

/// Zusätzliche Vertrauensanker gibt es nur mit dem Feature `test-ca`.
/// App-Builds enthalten diesen Code nicht.
#[cfg(feature = "test-ca")]
fn add_test_roots(roots: &mut RootCertStore, options: &Options) -> Result<(), MailError> {
    use rustls::pki_types::{pem::PemObject, CertificateDer};
    if let Some(pem) = &options.extra_roots_pem {
        for cert in CertificateDer::pem_slice_iter(pem) {
            let cert = cert.map_err(|_| MailError::InvalidInput)?;
            roots.add(cert).map_err(|_| MailError::InvalidInput)?;
        }
    }
    Ok(())
}

#[cfg(not(feature = "test-ca"))]
fn add_test_roots(_roots: &mut RootCertStore, _options: &Options) -> Result<(), MailError> {
    Ok(())
}

/// Ordnet einen Fehler des TLS-Handshakes einer verständlichen Klasse zu.
pub(crate) fn classify(error: &io::Error) -> MailError {
    let tls_error = error
        .get_ref()
        .and_then(|inner| inner.downcast_ref::<rustls::Error>());
    match tls_error {
        Some(rustls::Error::InvalidCertificate(reason)) => classify_certificate(reason),
        Some(_) => MailError::TlsFailed,
        None => match error.kind() {
            io::ErrorKind::UnexpectedEof
            | io::ErrorKind::ConnectionReset
            | io::ErrorKind::ConnectionAborted
            | io::ErrorKind::BrokenPipe => MailError::Network,
            _ => MailError::TlsFailed,
        },
    }
}

fn classify_certificate(reason: &CertificateError) -> MailError {
    match reason {
        CertificateError::Expired
        | CertificateError::ExpiredContext { .. }
        | CertificateError::NotValidYet
        | CertificateError::NotValidYetContext { .. } => MailError::TlsCertificateExpired,
        CertificateError::NotValidForName | CertificateError::NotValidForNameContext { .. } => {
            MailError::TlsHostnameMismatch
        }
        _ => MailError::TlsCertificateUntrusted,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn wrap(error: rustls::Error) -> io::Error {
        io::Error::new(io::ErrorKind::InvalidData, error)
    }

    #[test]
    fn certificate_errors_are_distinguished() {
        assert_eq!(
            classify(&wrap(rustls::Error::InvalidCertificate(
                CertificateError::Expired
            ))),
            MailError::TlsCertificateExpired
        );
        assert_eq!(
            classify(&wrap(rustls::Error::InvalidCertificate(
                CertificateError::NotValidForName
            ))),
            MailError::TlsHostnameMismatch
        );
        assert_eq!(
            classify(&wrap(rustls::Error::InvalidCertificate(
                CertificateError::UnknownIssuer
            ))),
            MailError::TlsCertificateUntrusted
        );
    }

    #[test]
    fn dropped_connection_is_a_network_error() {
        let eof = io::Error::new(io::ErrorKind::UnexpectedEof, "eof");
        assert_eq!(classify(&eof), MailError::Network);
    }

    #[test]
    fn production_connector_builds() {
        assert!(connector(&Options::production()).is_ok());
    }
}
