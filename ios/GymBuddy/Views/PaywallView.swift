import StoreKit
import SwiftUI

/// What Pro unlocks and the plans the App Store sells, like Strong's upgrade screen.
struct PaywallView: View {
    private let store = ProStore.shared
    @State private var buying: Product.ID?
    @State private var failed = false

    var body: some View {
        List {
            Section {
                VStack(spacing: 12) {
                    Image(systemName: "crown.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(.tint)
                    Text(store.isPro ? "You have GymBuddy Pro. Thanks for your support!" : "Get more out of every workout")
                        .font(.title3.bold())
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }
            Section {
                ForEach(ProFeature.allCases, id: \.self) { feature in
                    Label { Text(feature.label) } icon: { Image(systemName: "checkmark.circle.fill").foregroundStyle(.tint) }
                }
            }
            if !store.isPro {
                Section {
                    if store.products.isEmpty {
                        Text("Pro will be available to buy soon.").foregroundStyle(.secondary)
                    }
                    ForEach(store.products) { product in
                        Button {
                            buy(product)
                        } label: {
                            HStack {
                                Text(planLabel(product.id))
                                Spacer()
                                if buying == product.id { ProgressView() } else { Text(verbatim: product.displayPrice).bold() }
                            }
                        }
                        .disabled(buying != nil)
                    }
                    Button("Restore purchases") { Task { await store.restore() } }
                }
            }
            #if DEBUG
            Section {
                Toggle("Pro without purchase (debug builds)", isOn: Binding(
                    get: { store.debugOverride },
                    set: { store.setDebugOverride($0) }
                ))
            }
            #endif
        }
        .navigationTitle(Text(verbatim: "GymBuddy Pro"))
        .task { await store.load() }
        .alert("Purchase failed", isPresented: $failed) { Button("OK", role: .cancel) {} }
    }

    private func planLabel(_ id: String) -> LocalizedStringKey {
        switch id {
        case ProProducts.monthly: "Monthly"
        case ProProducts.yearly: "Yearly"
        default: "Lifetime"
        }
    }

    private func buy(_ product: Product) {
        buying = product.id
        Task {
            do { try await store.purchase(product) } catch { failed = true }
            buying = nil
        }
    }
}

/// Shown in place of a Pro feature for free users.
struct ProLockedView: View {
    let feature: ProFeature

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "lock.fill").font(.largeTitle).foregroundStyle(.tint)
            Text(feature.label).multilineTextAlignment(.center)
            NavigationLink("Unlock with Pro") { PaywallView() }
                .buttonStyle(.borderedProminent)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
