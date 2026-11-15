//! Gemeinsamer, lokaler Mail-Transportkern.
//! Diese erste Iteration validiert ausschliesslich den UniFFI-Binding-Pfad.
//! IMAP-Netzwerkzugriff und TLS folgen nach dem Integrations-Spike (ADR-006).

uniffi::include_scaffolding!("mail_transport");

pub fn transport_version() -> String {
    env!("CARGO_PKG_VERSION").to_owned()
}

/// In Phase 0 erlauben wir zunächst ausschliesslich implizites TLS (IMAPS).
/// STARTTLS auf Port 143 benötigt später eine explizite, getestete Policy.
pub fn is_valid_imaps_port(port: u16) -> bool {
    port == 993
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
}
