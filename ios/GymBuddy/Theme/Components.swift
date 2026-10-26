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

/// A number with a caption, for summaries and the profile.
struct StatTile: View {
    let value: String
    let label: LocalizedStringKey
    var systemImage: String?
    var tint: Color = Palette.primary

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            if let systemImage {
                Image(systemName: systemImage).font(.footnote.weight(.semibold)).foregroundStyle(tint)
            }
            Text(value).font(.stat(.title3)).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.caption).foregroundStyle(Palette.secondaryText).lineLimit(1)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.raised, in: RoundedRectangle(cornerRadius: Radius.medium, style: .continuous))
    }
}

/// A rounded label, e.g. a body part or "PR".
struct Pill: View {
    let text: Text
    let color: Color

    var body: some View {
        text
            .font(.caption2.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, Spacing.xxs)
            .background(color.opacity(0.14), in: Capsule())
    }
}

/// A tinted circle with the exercise's first letter, colored by body part.
struct ExerciseAvatar: View {
    let name: String
    let bodyPart: BodyPart
    var size: CGFloat = 40

    var body: some View {
        let color = Palette.color(for: bodyPart)
        Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
            .font(.system(size: size * 0.42, weight: .bold, design: .rounded))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .background(color.opacity(0.15), in: Circle())
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
