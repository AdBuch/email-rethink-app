package ch.emailrethink.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class InboxItem(val sender: String, val subject: String, val preview: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { InboxScreen(emptyList()) } }
    }
}

@Composable
fun InboxScreen(messages: List<InboxItem>) {
    Scaffold(topBar = { Text("Inbox", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.headlineMedium) }) { padding ->
        if (messages.isEmpty()) {
            Text("Noch keine Nachrichten. Kontoeinrichtung folgt.", Modifier.padding(padding).padding(20.dp))
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(messages) { message ->
                    ListItem(headlineContent = { Text(message.subject) },
                        overlineContent = { Text(message.sender) },
                        supportingContent = { Text(message.preview) })
                    HorizontalDivider()
                }
            }
        }
    }
}
