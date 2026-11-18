package ch.emailrethink.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Geheimnisse der App (Datenbankschlüssel, Mailpasswort).
 *
 * Ein AES-256-GCM-Schlüssel liegt nicht exportierbar im Android Keystore; die
 * Geheimnisse selbst werden damit verschlüsselt in einer privaten Datei abgelegt,
 * die vom Backup ausgeschlossen ist. Nichts davon wird geloggt.
 */
class SecretStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("geheimnisse", Context.MODE_PRIVATE)

    /** Liefert den Datenbankschlüssel; `created` ist wahr, wenn er neu erzeugt wurde. */
    fun databaseKey(): DatabaseKey {
        read(DATABASE_KEY)?.let { return DatabaseKey(it, created = false) }
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // Als Hex-Passphrase, damit Android und iOS denselben Schlüsselweg nutzen.
        val key = random.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
        random.fill(0)
        write(DATABASE_KEY, key)
        return DatabaseKey(key, created = true)
    }

    fun savePassword(accountId: String, password: String) =
        write(passwordName(accountId), password.toByteArray(Charsets.UTF_8))

    fun password(accountId: String): String? =
        read(passwordName(accountId))?.toString(Charsets.UTF_8)

    fun deletePassword(accountId: String) {
        preferences.edit().remove(passwordName(accountId)).apply()
    }

    /** Verwirft alle Geheimnisse, z. B. wenn der Keystore-Schlüssel verloren ist. */
    fun reset() {
        preferences.edit().clear().apply()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    class DatabaseKey(val bytes: ByteArray, val created: Boolean)

    private fun passwordName(accountId: String) = "passwort:$accountId"

    private fun write(name: String, plain: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val sealed = cipher.iv + cipher.doFinal(plain)
        preferences.edit().putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    private fun read(name: String): ByteArray? {
        val sealed = preferences.getString(name, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return null
        if (sealed.size <= IV_BYTES) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                wrappingKey(),
                GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
            )
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        }.getOrNull()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun wrappingKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "email-rethink-geheimnisse-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DATABASE_KEY = "datenbankschluessel"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
