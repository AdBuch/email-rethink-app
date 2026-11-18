package ch.emailrethink.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.emailrethink.store.InboxEntry
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val viewModel: MailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val screen by viewModel.screen.collectAsState()
                    when (val current = screen) {
                        Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        is Screen.Setup -> SetupScreen(current, viewModel::submitSetup)
                        is Screen.Inbox -> InboxScreen(
                            current,
                            onRefresh = viewModel::refresh,
                            onOpen = viewModel::open,
                            onRemoveAccount = viewModel::removeAccount,
                        )
                        is Screen.Message -> {
                            BackHandler(onBack = viewModel::closeMessage)
                            MessageScreen(current, onBack = viewModel::closeMessage)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(state: Screen.Setup, onSubmit: (SetupInput) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("993") }
    var username by rememberSaveable { mutableStateOf("") }
    // Das Passwort wird bewusst nicht über Prozess-Neustarts hinweg aufbewahrt.
    var password by androidx.compose.runtime.remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Konto einrichten", style = MaterialTheme.typography.headlineMedium)
        Text(
            "IMAP-Zugang deines Mailanbieters. Die Verbindung ist immer TLS-verschlüsselt; " +
                "das Passwort wird nur auf diesem Gerät im Android Keystore geschützt gespeichert.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("E-Mail-Adresse") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = host, onValueChange = { host = it }, label = { Text("IMAP-Server (z. B. imap.example.org)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port (TLS, meist 993)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = username, onValueChange = { username = it }, label = { Text("Benutzername (leer = E-Mail-Adresse)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Passwort oder App-Passwort") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        state.error?.let { ErrorText(it) }
        Button(
            onClick = { onSubmit(SetupInput(email, host, port, username, password)) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) "Verbinde …" else "Verbinden")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    state: Screen.Inbox,
    onRefresh: () -> Unit,
    onOpen: (InboxEntry) -> Unit,
    onRemoveAccount: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Posteingang", style = MaterialTheme.typography.headlineMedium)
                Text(state.account.email, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRemoveAccount) { Text("Konto entfernen") }
        }
        state.error?.let {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                ErrorText(it)
                if (state.entries.isNotEmpty()) {
                    Text("Angezeigt wird der zuletzt gespeicherte Stand.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Auch die leere Liste muss scrollbar sein, sonst greift die Zieh-Geste nicht.
            LazyColumn(Modifier.fillMaxSize()) {
                if (state.entries.isEmpty() && !state.refreshing) {
                    item {
                        Text(
                            "Keine Nachrichten. Zum Aktualisieren nach unten ziehen.",
                            Modifier.padding(20.dp),
                        )
                    }
                }
                items(state.entries, key = { it.id }) { entry ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(entry) },
                        overlineContent = {
                            Row {
                                Text(senderLabel(entry), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatDate(entry.receivedAtEpochSeconds))
                            }
                        },
                        headlineContent = {
                            Text(
                                entry.subject?.takeIf { it.isNotBlank() } ?: "(kein Betreff)",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (entry.seen) FontWeight.Normal else FontWeight.Bold,
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun MessageScreen(state: Screen.Message, onBack: () -> Unit) {
    var showHtml by rememberSaveable(state.entry.id) { mutableStateOf(false) }
    val body = state.body
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Posteingang") }
            Box(Modifier.weight(1f))
            if (body?.html != null && body.text != null) {
                TextButton(onClick = { showHtml = !showHtml }) {
                    Text(if (showHtml) "Als Text" else "Als HTML")
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                state.entry.subject?.takeIf { it.isNotBlank() } ?: "(kein Betreff)",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(senderLabel(state.entry, withAddress = true), style = MaterialTheme.typography.bodyMedium)
            Text(formatDate(state.entry.receivedAtEpochSeconds), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(Modifier.padding(top = 12.dp))
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(Modifier.padding(20.dp)) { ErrorText(state.error) }
            body == null || (body.text == null && body.html == null) ->
                Text("Diese Nachricht enthält keinen darstellbaren Text.", Modifier.padding(20.dp))
            body.html != null && (showHtml || body.text == null) -> Column(Modifier.fillMaxSize()) {
                Text(
                    "HTML-Ansicht ohne JavaScript und ohne externe Inhalte (Bilder, Tracker).",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                SafeHtmlView(body.html.orEmpty(), Modifier.fillMaxSize())
            }
            else -> SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(body.text.orEmpty(), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

private fun senderLabel(entry: InboxEntry, withAddress: Boolean = false): String {
    val name = entry.senderName?.takeIf { it.isNotBlank() }
    val address = entry.senderAddress?.takeIf { it.isNotBlank() }
    return when {
        name != null && address != null && withAddress -> "$name <$address>"
        name != null -> name
        address != null -> address
        else -> "(unbekannter Absender)"
    }
}

private fun formatDate(epochSeconds: Long): String =
    if (epochSeconds <= 0) {
        ""
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochSeconds * 1000))
    }
