package ch.emailrethink.store

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import ch.emailrethink.store.db.MailDatabase
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseFileContext

/** Öffnet die Maildatenbank – immer mit SQLCipher (ADR-007), nie unverschlüsselt. */
object MailDatabaseFactory {
    /**
     * @param keyHex zufälliger Datenbankschlüssel (hexadezimal) aus der iOS-Keychain.
     */
    @Throws(Exception::class)
    fun open(keyHex: String): InboxStore {
        require(keyHex.length >= 64) { "Datenbankschlüssel zu kurz" }
        val driver = NativeSqliteDriver(
            schema = MailDatabase.Schema,
            name = InboxStore.DATABASE_NAME,
            onConfiguration = { configuration ->
                configuration.copy(
                    encryptionConfig = DatabaseConfiguration.Encryption(key = keyHex),
                    extendedConfig = configuration.extendedConfig.copy(foreignKeyConstraints = true),
                )
            },
        )
        InboxStore.requireCipher(driver)
        return InboxStore(MailDatabase(driver), driver)
    }

    /** Entfernt die Datenbank samt Journal-/WAL-Dateien, z. B. wenn der Schlüssel verloren ist. */
    @Throws(Exception::class)
    fun delete() {
        DatabaseFileContext.deleteDatabase(InboxStore.DATABASE_NAME)
    }

    /** Verzeichnis der Datenbankdatei, damit die App es vom Backup ausschliessen kann. */
    fun databasePath(): String = DatabaseFileContext.databasePath(InboxStore.DATABASE_NAME, null)
}
