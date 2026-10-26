import Foundation
import Observation
import UIKit
import UserNotifications

/// The one rest timer of the app. A local notification fires at the end, so it works in the background.
@MainActor
@Observable
final class RestTimer {
    static let shared = RestTimer()

    private(set) var state: RestTimerState?
    private var label = ""
    private var finishTask: Task<Void, Never>?

    private static let notificationId = "rest-timer"

    func start(seconds: Int, exerciseName: String) {
        guard let next = RestTimerState.start(seconds: seconds) else { return }
        label = exerciseName
        apply(next)
    }

    func adjust(by deltaSeconds: Int) {
        guard let state else { return }
        apply(state.adjusted(by: deltaSeconds))
    }

    func skip() {
        apply(nil)
    }

    /// Asks once for permission to show the "rest is over" notification.
    static func requestAuthorization() {
        Task {
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
        }
    }

    private func apply(_ next: RestTimerState?) {
        state = next
        WatchBridge.shared.publish()
        finishTask?.cancel()
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.notificationId])
        guard let next else { return }

        let content = UNMutableNotificationContent()
        content.title = String(localized: "Rest is over")
        content.body = label.isEmpty ? String(localized: "Time for your next set.") : label
        content.sound = .default
        content.interruptionLevel = .timeSensitive
        let interval = max(next.endsAt.timeIntervalSinceNow, 1)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
        center.add(UNNotificationRequest(identifier: Self.notificationId, content: content, trigger: trigger))

        // While the app is open, clear the timer and buzz when it runs out.
        finishTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(interval))
            guard !Task.isCancelled, let self, self.state == next else { return }
            self.state = nil
            WatchBridge.shared.publish()
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }
}

/// Shows the rest notification as a banner while the app is in the foreground too.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate, Sendable {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}
