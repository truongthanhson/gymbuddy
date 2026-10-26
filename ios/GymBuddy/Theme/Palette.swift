import SwiftUI
import UIKit

/// "Ember" palette, shared with Android (ui/theme/Color.kt): a warm orange accent on warm neutrals.
enum Palette {
    static let primary = Color.accentColor
    static let teal = dynamic(light: 0x0E9F8E, dark: 0x4FD1C0)
    static let indigo = dynamic(light: 0x5B5FE0, dark: 0x9EA2FF)
    static let amber = dynamic(light: 0xE8930C, dark: 0xFFC14D)
    static let pink = dynamic(light: 0xE0457B, dark: 0xFF7EA8)
    static let success = dynamic(light: 0x1E9E5A, dark: 0x3DD68C)
    /// Personal records and Pro highlights.
    static let gold = amber

    /// Screen background behind cards and grouped lists.
    static let background = dynamic(light: 0xF6F5F2, dark: 0x0F0F12)
    /// Cards and grouped list rows.
    static let card = dynamic(light: 0xFFFFFF, dark: 0x17171B)
    /// Tiles inside a card, input fields and the tab bar.
    static let raised = dynamic(light: 0xF0EEEA, dark: 0x24242A)
    static let separator = dynamic(light: 0xE5E2DC, dark: 0x2E2D33)
    static let secondaryText = dynamic(light: 0x6A6872, dark: 0xA3A1AA)

    /// Series in order: primary, teal, indigo, amber, pink.
    static let chartSeries: [Color] = [primary, teal, indigo, amber, pink]
    static let chartGrid = separator
    static let chartLabel = secondaryText

    /// Each body part keeps one color everywhere: avatars, pills and charts by body part.
    static func color(for bodyPart: BodyPart) -> Color {
        switch bodyPart {
        case .chest: primary
        case .back: indigo
        case .legs: teal
        case .shoulders: amber
        case .arms: pink
        case .core: success
        case .cardio: .red
        case .olympic, .fullBody, .other: secondaryText
        }
    }

    private static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
