import SwiftUI

@main
struct GymBuddyWatchApp: App {
    init() {
        PhoneLink.shared.activate()
    }

    var body: some Scene {
        WindowGroup {
            WorkoutRemoteView()
        }
    }
}
