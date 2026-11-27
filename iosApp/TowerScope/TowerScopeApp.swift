import SwiftUI
import Shared

@main
struct TowerScopeApp: App {
    /// StoreKit 2 trial and unlock, handed to the shared Kotlin paywall before the UI appears.
    private let store: StoreKitBridge

    init() {
        let license = IosEdition.shared.license
        store = StoreKitBridge(license: license)
        license.bridge = store
        store.refresh()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .preferredColorScheme(.dark)
        }
    }
}

/// Hosts the shared Compose Multiplatform UI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
