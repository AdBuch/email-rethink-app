package ch.emailrethink.transport

import kotlin.coroutines.cancellation.CancellationException

/** Verbindungsdaten eines IMAP-Kontos. Das Passwort gehört bewusst nicht dazu. */
data class ImapAccountConfig(val host: String, val port: Int, val username: String)

data class MailHeader(
    val uid: Long,
    val senderName: String?,
    val senderAddress: String?,
    val subject: String?,
    /** INTERNALDATE als Unix-Zeit in Sekunden; 0 = unbekannt. */
    val internalDateEpochSeconds: Long,
    val messageId: String?,
    val seen: Boolean,
)

/** Momentaufnahme des Posteingangs; [headers] neueste zuerst. */
data class InboxSnapshot(
    val uidValidity: Long,
    val folders: List<String>,
    val headers: List<MailHeader>,
)

data class MailBody(val text: String?, val html: String?)

class MailTransportException(val kind: MailErrorKind) : Exception(kind.name)

/**
 * Fachliche Grenze zum Mail-Transport (ADR-006). Zustandslos: jeder Aufruf baut
 * eine eigene TLS-Verbindung auf. Der gemeinsame Rust-Kern implementiert sie –
 * auf Android über [ch.emailrethink.transport] `RustMailTransport`, auf iOS über
 * die Swift-Bindings desselben Kerns.
 */
interface MailTransport {
    @Throws(MailTransportException::class, CancellationException::class)
    suspend fun fetchInbox(account: ImapAccountConfig, secret: String, limit: Int = 50): InboxSnapshot

    @Throws(MailTransportException::class, CancellationException::class)
    suspend fun fetchBody(account: ImapAccountConfig, secret: String, uidValidity: Long, uid: Long): MailBody
}
