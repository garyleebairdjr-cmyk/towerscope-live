import Foundation
import Security
import StoreKit
import Shared

/// The App Store edition's 7-day trial and one-time unlock, with StoreKit 2.
///
/// - `towerscope_7_day_trial`: a free (Price Tier 0) non-consumable named "7-day Trial", as App Store Review
///   Guideline 3.1.1 allows for a time-based trial of a non-subscription app. Its purchase date is the trial
///   start, so it follows the person's Apple Account.
/// - `towerscope_unlock`: the paid non-consumable ($1.99 in the US) that unlocks the app for good.
///
/// If the trial product can't be loaded (not created yet, or the store is unreachable), the trial falls back to a
/// start date kept on the device in the Keychain and UserDefaults. That can be reset, and the paywall says so.
///
/// Kotlin talks to this through the `AppStoreBridge` protocol; this reports back with
/// `AppStoreLicenseManager.publish`. Everything is called and published on the main thread.
final class StoreKitBridge: NSObject, AppStoreBridge {
    static let unlockID = IosEdition.shared.UNLOCK_PRODUCT_ID
    static let trialID = IosEdition.shared.TRIAL_PRODUCT_ID

    private let license: AppStoreLicenseManager
    private var products: [String: Product] = [:]
    private var unlocked = false
    private var trialStart: Date?
    private var storeReady = false
    private var busy = false
    private var message: String?
    private var updates: Task<Void, Never>?

    init(license: AppStoreLicenseManager) {
        self.license = license
        super.init()
    }

    deinit { updates?.cancel() }

    // MARK: AppStoreBridge

    func refresh() {
        if updates == nil {
            // Purchases can finish outside the app (Ask to Buy, another device, a refund). Listen for the whole run.
            updates = Task { [weak self] in
                for await result in Transaction.updates {
                    if case .verified(let t) = result { await t.finish() }
                    await self?.loadEntitlements()
                }
            }
        }
        Task { @MainActor in
            await loadProducts()
            await loadEntitlements()
        }
    }

    func startTrial() {
        Task { @MainActor in
            busy = true; message = nil; publish()
            if let trial = products[Self.trialID] {
                await buy(trial)
            } else {
                // No trial product available: start the on-device trial instead.
                LocalTrial.begin(now: Date())
                trialStart = LocalTrial.start
            }
            busy = false
            await loadEntitlements()
        }
    }

    func purchaseUnlock() {
        Task { @MainActor in
            guard let unlock = products[Self.unlockID] else {
                await loadProducts()
                message = products[Self.unlockID] == nil
                    ? "The unlock isn't available from the App Store right now. Check your connection, then try again."
                    : nil
                publish()
                return
            }
            busy = true; message = nil; publish()
            await buy(unlock)
            busy = false
            await loadEntitlements()
        }
    }

    func restore() {
        Task { @MainActor in
            busy = true; message = nil; publish()
            do {
                try await AppStore.sync()
            } catch {
                message = "Couldn't reach the App Store to restore. Check your connection, then try again."
            }
            busy = false
            await loadEntitlements()
        }
    }

    // MARK: StoreKit

    @MainActor
    private func loadProducts() async {
        do {
            let list = try await Product.products(for: [Self.unlockID, Self.trialID])
            products = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
            storeReady = true
        } catch {
            storeReady = false
        }
    }

    @MainActor
    private func loadEntitlements() async {
        var hasUnlock = false
        var trialDate: Date?
        // StoreKit keeps these on the device, so this also works offline after a purchase.
        for await result in Transaction.currentEntitlements {
            guard case .verified(let t) = result, t.revocationDate == nil else { continue }
            if t.productID == Self.unlockID { hasUnlock = true }
            if t.productID == Self.trialID { trialDate = t.purchaseDate }
        }
        unlocked = hasUnlock
        // Use the earliest known start, so switching between the App Store trial and the local one never extends it.
        trialStart = [trialDate, LocalTrial.start].compactMap { $0 }.min()
        publish()
    }

    @MainActor
    private func buy(_ product: Product) async {
        do {
            switch try await product.purchase() {
            case .success(let result):
                switch result {
                case .verified(let t):
                    await t.finish()
                case .unverified:
                    message = "The App Store couldn't verify that purchase. Nothing was unlocked. Try Restore purchase."
                }
            case .pending:
                message = "Your purchase is waiting for approval (for example, Ask to Buy). TowerScope unlocks as soon as it's approved."
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            message = "The purchase didn't go through. Nothing was charged. You can try again."
        }
    }

    private func publish() {
        let startMs = trialStart.map { Int64($0.timeIntervalSince1970 * 1000) } ?? 0
        license.publish(
            unlocked: unlocked,
            trialStartMs: startMs,
            price: products[Self.unlockID]?.displayPrice,
            storeReady: storeReady,
            busy: busy,
            message: message
        )
    }
}

/// On-device trial start, used only when the free "7-day Trial" product isn't available. Kept in the Keychain
/// (which usually survives reinstalling on the same device) and in UserDefaults. It is not tamper-proof.
enum LocalTrial {
    private static let account = "trial_start"
    private static let service = "io.towerscope.live.trial"

    static var start: Date? {
        if let s = keychainRead() { return Date(timeIntervalSince1970: s) }
        let d = UserDefaults.standard.double(forKey: account)
        return d > 0 ? Date(timeIntervalSince1970: d) : nil
    }

    static func begin(now: Date) {
        guard start == nil else { return }
        let t = now.timeIntervalSince1970
        UserDefaults.standard.set(t, forKey: account)
        keychainWrite(t)
    }

    private static func keychainRead() -> Double? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data, let s = String(data: data, encoding: .utf8) else { return nil }
        return Double(s)
    }

    private static func keychainWrite(_ value: Double) {
        let data = Data(String(value).utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }
}
