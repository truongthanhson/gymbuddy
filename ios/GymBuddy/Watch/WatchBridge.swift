import Foundation
import SwiftData
import WatchConnectivity

/// Keeps the watch app in step with the workout on the iPhone (shared/watch-protocol.md):
/// shares the active workout as the application context and carries out the actions the watch sends.
@MainActor
final class WatchBridge: NSObject {
    static let shared = WatchBridge()

    private var context: ModelContext?
    private var lastJSON: String?

    func activate(context: ModelContext) {
        self.context = context
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
        // Every change to a workout is saved, so a save is the moment to update the watch.
        NotificationCenter.default.addObserver(forName: ModelContext.didSave, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { WatchBridge.shared.publish() }
        }
    }

    func publish() {
        guard let context, WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled else { return }
        let state = WatchStateBuilder.state(
            in: context,
            unit: WeightUnit(rawValue: UserDefaults.standard.string(forKey: SettingsKey.weightUnit) ?? "") ?? .kg,
            restEndsAt: RestTimer.shared.state?.endsAt
        )
        guard let json = state.encoded(), json != lastJSON else { return }
        do {
            try session.updateApplicationContext([WatchLink.jsonKey: json])
            lastJSON = json
        } catch {
            // The watch gets the next state instead.
        }
    }

    func handle(_ json: String) {
        guard let context, let action = WatchAction.decode(json) else { return }
        switch action.action {
        case WatchAction.completeSet:
            if let setId = action.setId { completeSet(setId, in: context) }
        case WatchAction.skipRest:
            RestTimer.shared.skip()
        default:
            break
        }
    }

    /// Same as ticking the set on the phone (ExerciseSection in ActiveWorkoutView), for a set not done yet.
    private func completeSet(_ setId: String, in context: ModelContext) {
        guard let uuid = UUID(uuidString: setId),
              let set = try? context.fetch(FetchDescriptor<WorkoutSet>(predicate: #Predicate { $0.id == uuid })).first,
              !set.completed, set.deletedAt == nil,
              let item = set.workoutExercise
        else { return }
        let exerciseId = item.exerciseId
        let exercise = try? context.fetch(FetchDescriptor<Exercise>(predicate: #Predicate { $0.id == exerciseId })).first
        let previous = WorkoutService.previousSets(for: exerciseId, in: context)
        let index = item.liveSets.firstIndex { $0.id == uuid } ?? -1
        WorkoutService.toggle(set, previous: previous.indices.contains(index) ? previous[index] : nil, in: context)
        let appDefault = UserDefaults.standard.object(forKey: SettingsKey.defaultRestSeconds) as? Int ?? defaultRestSeconds
        let seconds = resolveRestSeconds(
            workoutExerciseRest: item.restSeconds,
            exerciseDefault: exercise?.defaultRestSeconds,
            appDefault: appDefault
        )
        RestTimer.shared.start(seconds: seconds, exerciseName: exercise?.displayName ?? exerciseId)
    }
}

extension WatchBridge: WCSessionDelegate {
    nonisolated func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        Task { @MainActor in WatchBridge.shared.publish() }
    }

    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}

    /// The user switched to another watch: start over with it.
    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
        Task { @MainActor in WatchBridge.shared.lastJSON = nil }
    }

    nonisolated func sessionWatchStateDidChange(_ session: WCSession) {
        Task { @MainActor in WatchBridge.shared.publish() }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        guard let json = message[WatchLink.jsonKey] as? String else { return }
        Task { @MainActor in WatchBridge.shared.handle(json) }
    }

    /// Actions queued while the phone was out of reach.
    nonisolated func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
        guard let json = userInfo[WatchLink.jsonKey] as? String else { return }
        Task { @MainActor in WatchBridge.shared.handle(json) }
    }
}
