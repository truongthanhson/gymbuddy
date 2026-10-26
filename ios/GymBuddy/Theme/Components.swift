import SwiftUI

/// Spacing scale; views use these instead of ad-hoc values.
enum Spacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
}

enum Radius {
    static let small: CGFloat = 10
    static let medium: CGFloat = 16
    static let large: CGFloat = 20
}

extension Font {
    /// Big numbers in stats and summaries: rounded, with digits that line up.
    static func stat(_ style: Font.TextStyle = .title2) -> Font {
        .system(style, design: .rounded).weight(.bold).monospacedDigit()
    }
}

/// The app's flat card: white on the warm background in light mode, a lifted gray in dark mode.
struct CardBackground: ViewModifier {
    var padding: CGFloat = Spacing.lg

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.large, style: .continuous))
    }
}

extension View {
    func card(padding: CGFloat = Spacing.lg) -> some View {
        modifier(CardBackground(padding: padding))
    }

    /// Grouped lists and scroll views on the warm app background with white rows.
    func appBackground() -> some View {
        scrollContentBackground(.hidden).background(Palette.background)
    }
}

/// The main call to action: a full-width filled button in the accent color.
struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Palette.primary, in: RoundedRectangle(cornerRadius: Radius.medium, style: .continuous))
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == PrimaryButtonStyle {
    static var primary: PrimaryButtonStyle { PrimaryButtonStyle() }
}
