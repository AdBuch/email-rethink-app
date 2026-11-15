import SwiftUI

struct InboxMessage: Identifiable {
    let id: String
    let sender: String
    let subject: String
    let preview: String
}

struct InboxView: View {
    let messages: [InboxMessage]

    var body: some View {
        NavigationStack {
            Group {
                if messages.isEmpty {
                    ContentUnavailableView("Keine Nachrichten", systemImage: "tray",
                        description: Text("Die Kontoeinrichtung folgt."))
                } else {
                    List(messages) { message in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(message.sender).font(.caption).foregroundStyle(.secondary)
                            Text(message.subject).font(.headline)
                            Text(message.preview).font(.subheadline).lineLimit(2)
                        }
                    }
                }
            }
            .navigationTitle("Inbox")
        }
    }
}
