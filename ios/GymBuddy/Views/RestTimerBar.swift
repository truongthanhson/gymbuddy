import SwiftUI

struct RestTimerBar: View {
    let state: RestTimerState

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.25)) { timeline in
            VStack(spacing: 8) {
                ProgressView(value: state.progress(at: timeline.date))
                HStack {
                    Button("−15s") { RestTimer.shared.adjust(by: -15) }
                        .buttonStyle(.bordered)
                    Text(verbatim: formatElapsed(state.remainingSeconds(at: timeline.date)))
                        .font(.title2.monospacedDigit())
                        .frame(maxWidth: .infinity)
                    Button("+15s") { RestTimer.shared.adjust(by: 15) }
                        .buttonStyle(.bordered)
                    Button("Skip") { RestTimer.shared.skip() }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
            .background(.bar)
        }
    }
}
