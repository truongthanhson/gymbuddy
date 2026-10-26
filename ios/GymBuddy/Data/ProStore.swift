import Foundation
import Observation
import StoreKit

/// GymBuddy Pro through StoreKit 2. The last known state is kept so Pro works offline;
/// the App Store's entitlements replace it whenever they load.
@MainActor
@Observable
final class ProStore {
    static let shared = ProStore()

    private static let purchasedKey = "pro.purchased"
    private static let debugKey = "pro.debugOverride"

    /// Sorted monthly, yearly, lifetime; empty until the store answers or when the products don't exist yet.
    private(set) var products: [Product] = []
    private(set) var purchased: Bool
    /// Debug builds can turn Pro on without a purchase, to try the locked screens.
    private(set) var debugOverride: Bool

    var isPro: Bool { purchased || debugOverride }

    @ObservationIgnored private var updates: Task<Void, Never>?

    private init() {
        purchased = UserDefaults.standard.bool(forKey: Self.purchasedKey)
        #if DEBUG
        debugOverride = UserDefaults.standard.bool(forKey: Self.debugKey)
        #else
        debugOverride = false
        #endif
    }

    func start() {
        guard updates == nil else { return }
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let transaction) = result { await transaction.finish() }
                await self?.refreshEntitlements()
            }
        }
        Task { await load() }
    }

    func load() async {
        if let loaded = try? await Product.products(for: ProProducts.all) {
            products = loaded.sorted { ProProducts.all.firstIndex(of: $0.id)! < ProProducts.all.firstIndex(of: $1.id)! }
        }
        await refreshEntitlements()
    }

    func purchase(_ product: Product) async throws {
        if case .success(let result) = try await product.purchase(), case .verified(let transaction) = result {
            await transaction.finish()
            await refreshEntitlements()
        }
    }

    /// "Restore purchases": syncs with the App Store, which may ask the user to sign in.
    func restore() async {
        try? await AppStore.sync()
        await load()
    }

    func setDebugOverride(_ enabled: Bool) {
        #if DEBUG
        UserDefaults.standard.set(enabled, forKey: Self.debugKey)
        debugOverride = enabled
        #endif
    }

    private func refreshEntitlements() async {
        var owned = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result,
               ProProducts.all.contains(transaction.productID),
               transaction.revocationDate == nil {
                owned = true
            }
        }
        purchased = owned
        UserDefaults.standard.set(owned, forKey: Self.purchasedKey)
    }
}
