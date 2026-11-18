package ch.emailrethink.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.emailrethink.store.InboxEntry
import ch.emailrethink.store.InboxStore
import ch.emailrethink.store.MailDatabaseFactory
import ch.emailrethink.store.StoredAccount
import ch.emailrethink.transport.ImapAccountConfig
import ch.emailrethink.transport.MailBody
import ch.emailrethink.transport.MailErrorKind
import ch.emailrethink.transport.MailTransport
import ch.emailrethink.transport.MailTransportException
import ch.emailrethink.transport.RustMailTransport
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Loading : Screen
    data class Setup(val busy: Boolean = false, val error: String? = null) : Screen
    data class Inbox(
        val account: StoredAccount,
        val entries: List<InboxEntry> = emptyList(),
        val refreshing: Boolean = false,
        val error: String? = null,
    ) : Screen
    data class Message(
        val inbox: Inbox,
        val entry: InboxEntry,
        val body: MailBody? = null,
        val loading: Boolean = true,
        val error: String? = null,
    ) : Screen
}

data class SetupInput(
    val email: String,
    val host: String,
    val port: String,
    val username: String,
    val password: String,
)

class MailViewModel(application: Application) : AndroidViewModel(application) {
    private val secrets = SecretStore(application)
    private val transport: MailTransport = RustMailTransport()
    private var store: InboxStore? = null

    private val mutableScreen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = mutableScreen.asStateFlow()

    init {
        viewModelScope.launch {
            val opened = withContext(Dispatchers.IO) { runCatching { openStore() } }
            val current = opened.getOrNull()
            store = current
            if (current == null) {
                mutableScreen.value = Screen.Setup(
                    error = "Die verschlüsselte Datenbank konnte nicht geöffnet werden. Bitte die App neu starten.",
                )
                return@launch
            }
            val account = withContext(Dispatchers.IO) { current.account() }
            if (account == null || secrets.password(account.id) == null) {
                mutableScreen.value = Screen.Setup()
            } else {
                showInbox(account, refresh = true)
            }
        }
    }

    /**
     * Öffnet die Datenbank mit dem Schlüssel aus dem Keystore. Ist der Schlüssel
     * verloren (neues Gerät, Keystore zurückgesetzt), lässt sich die alte Datei
     * nicht mehr lesen: Sie wird gelöscht und die App beginnt mit der
     * Kontoeinrichtung neu – nie mit einer unverschlüsselten Datenbank (ADR-007).
     */
    private fun openStore(): InboxStore {
        val context = getApplication<Application>()
        val key = secrets.databaseKey()
        if (key.created) MailDatabaseFactory.delete(context)
        return try {
            MailDatabaseFactory.open(context, key.bytes).also { it.account() }
        } catch (error: Exception) {
            if (error is ch.emailrethink.store.DatabaseNotEncryptedException) throw error
            MailDatabaseFactory.delete(context)
            secrets.reset()
            MailDatabaseFactory.open(context, secrets.databaseKey().bytes)
        }
    }

    fun submitSetup(input: SetupInput) {
        val current = store ?: return
        val port = input.port.trim().toIntOrNull()
        val host = input.host.trim()
        val email = input.email.trim()
        val username = input.username.trim().ifEmpty { email }
        if (email.isEmpty() || host.isEmpty() || port == null || port !in 1..65535 || input.password.isEmpty()) {
            mutableScreen.value = Screen.Setup(error = MailErrorKind.INVALID_INPUT.userMessage)
            return
        }
        mutableScreen.value = Screen.Setup(busy = true)
        viewModelScope.launch {
            val account = StoredAccount(UUID.randomUUID().toString(), email, host, port, username)
            try {
                // Erst prüfen, dann speichern: Ein Konto wird nur nach erfolgreicher Anmeldung angelegt.
                val snapshot = transport.fetchInbox(account.toConfig(), input.password, INBOX_LIMIT)
                withContext(Dispatchers.IO) {
                    current.account()?.let { old ->
                        current.deleteAccount(old.id)
                        secrets.deletePassword(old.id)
                    }
                    secrets.savePassword(account.id, input.password)
                    current.saveAccount(account)
                    current.applySnapshot(account.id, snapshot)
                }
                showInbox(account, refresh = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableScreen.value = Screen.Setup(error = describe(error))
            }
        }
    }

    fun refresh() {
        val inbox = mutableScreen.value as? Screen.Inbox ?: return
        if (inbox.refreshing) return
        val current = store ?: return
        mutableScreen.value = inbox.copy(refreshing = true, error = null)
        viewModelScope.launch {
            val account = inbox.account
            val error = try {
                val password = secrets.password(account.id)
                    ?: throw MailTransportException(MailErrorKind.AUTHENTICATION_FAILED)
                val snapshot = transport.fetchInbox(account.toConfig(), password, INBOX_LIMIT)
                withContext(Dispatchers.IO) { current.applySnapshot(account.id, snapshot) }
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                describe(error)
            }
            val entries = withContext(Dispatchers.IO) { current.inbox(account.id, INBOX_LIMIT) }
            mutableScreen.update { shown ->
                if (shown is Screen.Inbox) shown.copy(entries = entries, refreshing = false, error = error) else shown
            }
        }
    }

    fun open(entry: InboxEntry) {
        val inbox = mutableScreen.value as? Screen.Inbox ?: return
        val current = store ?: return
        mutableScreen.value = Screen.Message(inbox.copy(refreshing = false), entry)
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { current.body(entry.id) }
            if (cached != null) {
                updateMessage(entry) { it.copy(body = cached, loading = false) }
                return@launch
            }
            try {
                val account = inbox.account
                val password = secrets.password(account.id)
                    ?: throw MailTransportException(MailErrorKind.AUTHENTICATION_FAILED)
                val body = transport.fetchBody(account.toConfig(), password, entry.uidValidity, entry.uid)
                withContext(Dispatchers.IO) {
                    current.saveBody(entry.id, body, System.currentTimeMillis() / 1000)
                }
                updateMessage(entry) { it.copy(body = body, loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateMessage(entry) { it.copy(loading = false, error = describe(error)) }
            }
        }
    }

    fun closeMessage() {
        val message = mutableScreen.value as? Screen.Message ?: return
        mutableScreen.value = message.inbox
    }

    fun removeAccount() {
        val inbox = mutableScreen.value as? Screen.Inbox ?: return
        val current = store ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                current.deleteAccount(inbox.account.id)
                secrets.deletePassword(inbox.account.id)
            }
            mutableScreen.value = Screen.Setup()
        }
    }

    private suspend fun showInbox(account: StoredAccount, refresh: Boolean) {
        val current = store ?: return
        // Zuerst der gespeicherte Stand – das funktioniert auch offline.
        val entries = withContext(Dispatchers.IO) { current.inbox(account.id, INBOX_LIMIT) }
        mutableScreen.value = Screen.Inbox(account, entries)
        if (refresh) refresh()
    }

    private fun updateMessage(entry: InboxEntry, change: (Screen.Message) -> Screen.Message) {
        mutableScreen.update { shown ->
            if (shown is Screen.Message && shown.entry.id == entry.id) change(shown) else shown
        }
    }

    private fun describe(error: Exception): String =
        (error as? MailTransportException)?.kind?.userMessage ?: MailErrorKind.UNKNOWN.userMessage

    private fun StoredAccount.toConfig() = ImapAccountConfig(host, port, username)

    override fun onCleared() {
        store?.close()
    }

    private companion object {
        const val INBOX_LIMIT = 50
    }
}
