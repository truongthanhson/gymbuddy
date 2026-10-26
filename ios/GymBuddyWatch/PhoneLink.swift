import Foundation
import Observation
import WatchConnectivity

/// The watch's view of the iPhone: the state the phone shares, and a way to send it actions.
@MainActor
@Observable
final class PhoneLink: NSObject {
    static let shared = PhoneLink()

    /// Nil until the phone has shared anything.
    private(set) var state: WatchState?

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func completeSet(_ id: String) {
        // Show the tick right away; the phone's next state confirms it.
        if var workout = state?.workout {
            for e in workout.exercises.indices {
                for s in workout.exercises[e].sets.indices where workout.exercises[e].sets[s].id == id {
                    workout.exercises[e].sets[s].done = true
                }
            }
            state = WatchState(workout: workout)
        }
        send(WatchAction(action: WatchAction.completeSet, setId: id))
    }

    func skipRest() {
        if var workout = state?.workout {
            workout.restEndsAt = nil
            state = WatchState(workout: workout)
        }
        send(WatchAction(action: WatchAction.skipRest))
    }

    fileprivate func receive(_ json: String) {
        if let next = WatchState.decode(json) { state = next }
    }

    private func send(_ action: WatchAction) {
        guard let json = action.encoded() else { return }
        let session = WCSession.default
        guard session.activationState == .activated else { return }
        let payload = [WatchLink.jsonKey: json]
        if session.isReachable {
            // Queue it for later if the phone can't take it now.
            session.sendMessage(payload, replyHandler: nil) { _ in WCSession.default.transferUserInfo(payload) }
        } else {
            session.transferUserInfo(payload)
        }
    }
}

extension PhoneLink: WCSessionDelegate {
    nonisolated func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        guard let json = session.receivedApplicationContext[WatchLink.jsonKey] as? String else { return }
        Task { @MainActor in PhoneLink.shared.receive(json) }
    }

    nonisolated func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        guard let json = applicationContext[WatchLink.jsonKey] as? String else { return }
        Task { @MainActor in PhoneLink.shared.receive(json) }
    }
}
