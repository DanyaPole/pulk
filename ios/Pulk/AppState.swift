import Combine
import SwiftUI

final class AppState: ObservableObject {
    @Published var text = "" {
        didSet { copied = false }
    }
    @Published var language = "RU"
    @Published var bigText: String?
    @Published var copied = false
    @Published var leftHanded = false {
        didSet { settings.leftHanded = leftHanded }
    }

    let bridge = PulkBridge(bundle: .main)
    let speech = Speech()
    let settings = KeyboardSettings()

    init() {
        leftHanded = settings.leftHanded
    }
}
