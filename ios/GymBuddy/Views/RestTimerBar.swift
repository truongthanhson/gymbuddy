import SwiftUI

struct RestTimerBar: View {
    let state: RestTimerState

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.25)) { timeline in
            // A dark floating pill that stands out from the white set cards in both themes.
            VStack(spacing: Spacing.sm) {
                HStack(spacing: Spacing.md) {
                    Text(verbatim: formatElapsed(state.remainingSeconds(at: timeline.date)))
                        .font(.stat(.title))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Button("−15s") { RestTimer.shared.adjust(by: -15) }
                    Button("+15s") { RestTimer.shared.adjust(by: 15) }
                    Button("Skip") { RestTimer.shared.skip() }
                        .fontWeight(.semibold)
                        .foregroundStyle(Palette.primary)
                }
                .buttonStyle(.plain)
                ProgressView(value: state.progress(at: timeline.date))
                    .tint(Palette.primary)
            }
            .foregroundStyle(Color(uiColor: .systemBackground))
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.md)
            .background(Color(uiColor: .label), in: RoundedRectangle(cornerRadius: Radius.large, style: .continuous))
            .shadow(color: .black.opacity(0.2), radius: 12, y: 4)
            .padding(.horizontal, Spacing.md)
            .padding(.bottom, Spacing.sm)
        }
    }
}
