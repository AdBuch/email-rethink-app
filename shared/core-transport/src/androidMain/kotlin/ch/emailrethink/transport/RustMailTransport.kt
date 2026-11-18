package ch.emailrethink.transport

import ch.emailrethink.transport.rust.ImapAccount
import ch.emailrethink.transport.rust.MailException
import ch.emailrethink.transport.rust.fetchBody as rustFetchBody
import ch.emailrethink.transport.rust.fetchInbox as rustFetchInbox

/** Android-Adapter: delegiert an den gemeinsamen Rust-Kern (UniFFI, ADR-006). */
class RustMailTransport : MailTransport {
    override suspend fun fetchInbox(account: ImapAccountConfig, secret: String, limit: Int): InboxSnapshot =
        translatingErrors {
            val snapshot = rustFetchInbox(account.toRust(), secret, limit.toUInt())
            InboxSnapshot(
                uidValidity = snapshot.uidValidity.toLong(),
                folders = snapshot.folders,
                headers = snapshot.headers.map {
                    MailHeader(
                        uid = it.uid.toLong(),
                        senderName = it.senderName,
                        senderAddress = it.senderAddress,
                        subject = it.subject,
                        internalDateEpochSeconds = it.internalDateEpochSeconds,
                        messageId = it.messageId,
                        seen = it.seen,
                    )
                },
            )
        }

    override suspend fun fetchBody(account: ImapAccountConfig, secret: String, uidValidity: Long, uid: Long): MailBody =
        translatingErrors {
            val body = rustFetchBody(account.toRust(), secret, uidValidity.toUInt(), uid.toUInt())
            MailBody(text = body.text, html = body.html)
        }

    private fun ImapAccountConfig.toRust(): ImapAccount {
        if (port !in 1..65535) throw MailTransportException(MailErrorKind.INVALID_INPUT)
        return ImapAccount(host = host, port = port.toUShort(), username = username)
    }

    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
            block()
        } catch (error: MailException) {
            throw MailTransportException(MailErrorKind.fromRustName(error::class.simpleName))
        }
}
