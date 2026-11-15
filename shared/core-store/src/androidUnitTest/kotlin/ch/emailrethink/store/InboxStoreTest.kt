package ch.emailrethink.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ch.emailrethink.store.db.MailDatabase
import ch.emailrethink.transport.InboxSnapshot
import ch.emailrethink.transport.MailBody
import ch.emailrethink.transport.MailHeader
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Prüft die Speicherlogik mit einer In-Memory-SQLite auf der JVM.
 * Die Verschlüsselung selbst (SQLCipher) ist hier bewusst nicht Gegenstand.
 */
class InboxStoreTest {
    private val account = StoredAccount("konto-1", "test@example.org", "imap.example.org", 993, "test")

    private fun newStore(): InboxStore {
        val driver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") },
        )
        MailDatabase.Schema.create(driver)
        return InboxStore(MailDatabase(driver), driver).also { it.saveAccount(account) }
    }

    private fun header(uid: Long, subject: String, seen: Boolean = false) =
        MailHeader(uid, "Absender $uid", "a$uid@example.org", subject, 1_700_000_000L + uid, "<$uid@example.org>", seen)

    private fun snapshot(uidValidity: Long, vararg headers: MailHeader) =
        InboxSnapshot(uidValidity, listOf("INBOX"), headers.sortedByDescending { it.uid })

    @Test
    fun kontoWirdGespeichertUndGelesen() {
        val store = newStore()
        assertEquals(account, store.account())
    }

    @Test
    fun momentaufnahmeWirdNeuesteZuerstGeliefert() {
        val store = newStore()
        store.applySnapshot(account.id, snapshot(7, header(1, "eins"), header(2, "zwei"), header(3, "drei")))

        val inbox = store.inbox(account.id, 50)
        assertEquals(listOf(3L, 2L, 1L), inbox.map { it.uid })
        assertEquals("drei", inbox.first().subject)
        assertEquals(7L, inbox.first().uidValidity)
        assertEquals(2, store.inbox(account.id, 2).size)
    }

    @Test
    fun wiederholterAbrufAktualisiertUndBehaeltInhalte() {
        val store = newStore()
        store.applySnapshot(account.id, snapshot(7, header(1, "eins"), header(2, "zwei")))
        val id = store.inbox(account.id, 50).first { it.uid == 2L }.id
        store.saveBody(id, MailBody("Text", "<p>Text</p>"), 1L)

        store.applySnapshot(account.id, snapshot(7, header(1, "eins"), header(2, "zwei", seen = true), header(3, "drei")))

        val inbox = store.inbox(account.id, 50)
        assertEquals(3, inbox.size)
        assertEquals(true, inbox.first { it.uid == 2L }.seen)
        assertEquals(MailBody("Text", "<p>Text</p>"), store.body(id))
    }

    @Test
    fun aufDemServerGeloeschteNachrichtVerschwindetSamtInhalt() {
        val store = newStore()
        store.applySnapshot(account.id, snapshot(7, header(1, "eins"), header(2, "zwei"), header(3, "drei")))
        val id = store.inbox(account.id, 50).first { it.uid == 2L }.id
        store.saveBody(id, MailBody("Text", null), 1L)

        store.applySnapshot(account.id, snapshot(7, header(1, "eins"), header(3, "drei")))

        assertEquals(listOf(3L, 1L), store.inbox(account.id, 50).map { it.uid })
        assertNull(store.body(id), "Fremdschlüssel-Kaskade muss den Inhalt entfernen")
    }

    @Test
    fun uidValidityWechselVerwirftAlleAltenNachrichten() {
        val store = newStore()
        store.applySnapshot(account.id, snapshot(7, header(1, "alt-1"), header(2, "alt-2")))
        val oldId = store.inbox(account.id, 50).first().id
        store.saveBody(oldId, MailBody("alt", null), 1L)

        // Gleiche UIDs, andere UIDVALIDITY: Es sind andere Nachrichten.
        store.applySnapshot(account.id, snapshot(8, header(1, "neu-1")))

        val inbox = store.inbox(account.id, 50)
        assertEquals(listOf("neu-1"), inbox.map { it.subject })
        assertEquals(8L, inbox.single().uidValidity)
        assertNull(store.body(oldId))
    }

    @Test
    fun leereMomentaufnahmeLeertDenPosteingang() {
        val store = newStore()
        store.applySnapshot(account.id, snapshot(7, header(1, "eins")))
        store.applySnapshot(account.id, snapshot(7))
        assertEquals(emptyList(), store.inbox(account.id, 50))
    }

    @Test
    fun inhaltFuerEntfernteNachrichtWirdNichtGespeichert() {
        val store = newStore()
        store.saveBody("gibt-es-nicht", MailBody("x", null), 1L)
        assertNull(store.body("gibt-es-nicht"))
    }

    @Test
    fun kontoLoeschenEntferntAlles() {
        val store = newStore()
        store.applySnapshot(account.id, snapshot(7, header(1, "eins")))
        val id = store.inbox(account.id, 50).single().id
        store.saveBody(id, MailBody("x", null), 1L)

        store.deleteAccount(account.id)

        assertNull(store.account())
        assertEquals(emptyList(), store.inbox(account.id, 50))
        assertNull(store.body(id))
    }

    @Test
    fun unverschluesselteDatenbankWirdAbgelehnt() {
        // Die JDBC-SQLite ist nicht SQLCipher: genau dieser Fall muss auffallen.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        assertFailsWith<DatabaseNotEncryptedException> { InboxStore.requireCipher(driver) }
        assertNotNull(driver)
    }
}
