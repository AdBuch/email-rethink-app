package ch.emailrethink.store

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import ch.emailrethink.store.db.MailDatabase
import ch.emailrethink.transport.InboxSnapshot
import ch.emailrethink.transport.MailBody

data class StoredAccount(
    val id: String,
    val email: String,
    val host: String,
    val port: Int,
    val username: String,
)

data class InboxEntry(
    val id: String,
    val uid: Long,
    val uidValidity: Long,
    val senderName: String?,
    val senderAddress: String?,
    val subject: String?,
    /** Unix-Zeit in Sekunden; 0 = unbekannt. */
    val receivedAtEpochSeconds: Long,
    val seen: Boolean,
)

class DatabaseNotEncryptedException :
    IllegalStateException("Die Datenbank ist nicht mit SQLCipher verschlüsselt; Öffnen abgelehnt (ADR-007).")

/**
 * Zugriff auf die lokale, verschlüsselte Maildatenbank. Plattformunabhängig;
 * geöffnet wird sie über `MailDatabaseFactory` der jeweiligen Plattform.
 */
class InboxStore(private val database: MailDatabase, private val driver: SqlDriver) {
    private val queries get() = database.mailQueries

    /** v0.1 kennt genau ein Konto. */
    @Throws(Exception::class)
    fun account(): StoredAccount? =
        queries.selectAccounts().executeAsList().firstOrNull()?.let {
            StoredAccount(
                id = it.id,
                email = it.email,
                host = it.imap_host.orEmpty(),
                port = (it.imap_port ?: 993L).toInt(),
                username = it.username,
            )
        }

    @Throws(Exception::class)
    fun saveAccount(account: StoredAccount) {
        queries.upsertAccount(
            id = account.id,
            email = account.email,
            display_name = null,
            transport_kind = TRANSPORT_IMAP,
            imap_host = account.host,
            imap_port = account.port.toLong(),
            username = account.username,
        )
    }

    /** Löscht das Konto samt Ordnern, Nachrichten und Inhalten (Fremdschlüssel-Kaskade). */
    @Throws(Exception::class)
    fun deleteAccount(accountId: String) {
        queries.deleteAccount(accountId)
    }

    /**
     * Übernimmt eine Momentaufnahme des Posteingangs.
     * - Geänderte UIDVALIDITY: alle bisherigen Nachrichten des Ordners werden verworfen.
     * - Gleiche UIDVALIDITY: Nachrichten im abgerufenen UID-Bereich, die fehlen, wurden
     *   auf dem Server gelöscht und verschwinden lokal.
     */
    @Throws(Exception::class)
    fun applySnapshot(accountId: String, snapshot: InboxSnapshot) {
        val folderId = folderId(accountId)
        database.transaction {
            val known = queries.selectFolder(accountId, INBOX).executeAsOneOrNull()
            if (known != null && known.uid_validity != snapshot.uidValidity) {
                queries.deleteMessagesOfFolder(folderId)
            }
            queries.upsertFolder(
                id = folderId,
                account_id = accountId,
                remote_name = INBOX,
                display_name = "Posteingang",
                uid_validity = snapshot.uidValidity,
            )
            val uids = snapshot.headers.map { it.uid }
            if (uids.isEmpty()) {
                queries.deleteMessagesOfFolder(folderId)
            } else {
                queries.deleteVanishedMessages(folderId, uids.min(), uids)
            }
            snapshot.headers.forEach { header ->
                queries.upsertMessage(
                    id = messageId(accountId, snapshot.uidValidity, header.uid),
                    folder_id = folderId,
                    remote_uid = header.uid,
                    message_id_header = header.messageId,
                    sender = header.senderName,
                    sender_address = header.senderAddress,
                    subject = header.subject,
                    sent_at = null,
                    received_at = header.internalDateEpochSeconds.takeIf { it > 0 },
                    is_read = if (header.seen) 1L else 0L,
                    snippet = null,
                )
            }
        }
    }

    /** Posteingang aus der Datenbank (auch offline), neueste zuerst. */
    @Throws(Exception::class)
    fun inbox(accountId: String, limit: Int): List<InboxEntry> =
        queries.selectInbox(accountId, INBOX, limit.toLong()).executeAsList().map {
            InboxEntry(
                id = it.id,
                uid = it.remote_uid,
                uidValidity = it.uid_validity ?: 0L,
                senderName = it.sender,
                senderAddress = it.sender_address,
                subject = it.subject,
                receivedAtEpochSeconds = it.received_at ?: 0L,
                seen = it.is_read != 0L,
            )
        }

    @Throws(Exception::class)
    fun body(messageId: String): MailBody? =
        queries.selectBody(messageId).executeAsOneOrNull()?.let {
            MailBody(text = it.text_content, html = it.html_content)
        }

    /** Speichert den Inhalt; ohne Wirkung, wenn die Nachricht inzwischen entfernt wurde. */
    @Throws(Exception::class)
    fun saveBody(messageId: String, body: MailBody, nowEpochSeconds: Long) {
        database.transaction {
            if (queries.selectMessage(messageId).executeAsOneOrNull() != null) {
                queries.upsertBody(messageId, body.text, body.html, nowEpochSeconds)
            }
        }
    }

    fun close() {
        driver.close()
    }

    companion object {
        const val INBOX = "INBOX"
        const val DATABASE_NAME = "mail.db"
        private const val TRANSPORT_IMAP = "imap"

        fun folderId(accountId: String): String = "$accountId:$INBOX"

        fun messageId(accountId: String, uidValidity: Long, uid: Long): String =
            "$accountId:$INBOX:$uidValidity:$uid"

        /**
         * Öffnen wird abgelehnt, wenn die Verbindung nicht von SQLCipher stammt.
         * Eine unverschlüsselte SQLite würde `PRAGMA key` stillschweigend ignorieren.
         */
        fun requireCipher(driver: SqlDriver) {
            val version = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA cipher_version",
                mapper = { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
                },
                parameters = 0,
            ).value
            if (version.isNullOrBlank()) {
                driver.close()
                throw DatabaseNotEncryptedException()
            }
        }
    }
}
