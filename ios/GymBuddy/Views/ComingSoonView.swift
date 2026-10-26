import SwiftUI

struct ComingSoonView: View {
    let title: LocalizedStringKey

    var body: some View {
        ContentUnavailableView(title, systemImage: "hammer", description: Text("Coming soon"))
    }
}
