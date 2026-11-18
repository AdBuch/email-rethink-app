package ch.emailrethink.store

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ch.emailrethink.store.db.MailDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Öffnet die Maildatenbank – immer mit SQLCipher (ADR-007), nie unverschlüsselt. */
object MailDatabaseFactory {
    /**
     * @param passphrase zufälliger Datenbankschlüssel aus dem Android Keystore.
     */
    fun open(context: Context, passphrase: ByteArray): InboxStore {
        require(passphrase.size >= 32) { "Datenbankschlüssel zu kurz" }
        System.loadLibrary("sqlcipher")
        val driver = AndroidSqliteDriver(
            schema = MailDatabase.Schema,
            context = context.applicationContext,
            name = InboxStore.DATABASE_NAME,
            factory = SupportOpenHelperFactory(passphrase),
            callback = object : AndroidSqliteDriver.Callback(MailDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    // Ohne dieses Pragma wirken ON DELETE CASCADE nicht (Review W6).
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
        InboxStore.requireCipher(driver)
        return InboxStore(MailDatabase(driver), driver)
    }

    /** Entfernt die Datenbank samt Journal-/WAL-Dateien, z. B. wenn der Schlüssel verloren ist. */
    fun delete(context: Context) {
        context.applicationContext.deleteDatabase(InboxStore.DATABASE_NAME)
    }
}
