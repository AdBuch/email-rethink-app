import SwiftUI

@main
struct EmailRethinkApp: App {
    var body: some Scene {
        WindowGroup { InboxView(messages: []) }
    }
}
