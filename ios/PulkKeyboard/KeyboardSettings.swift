import Foundation

struct KeyboardSettings {
    private let defaults = UserDefaults.standard
    var leftHanded: Bool {
        get { defaults.bool(forKey: "leftHanded") }
        nonmutating set { defaults.set(newValue, forKey: "leftHanded") }
    }
    var starHeight: Double {
        get {
            guard defaults.object(forKey: "starHeight") != nil else { return 240 }
            let value = defaults.double(forKey: "starHeight")
            return value.isFinite ? min(max(value, 180), 300) : 240
        }
        nonmutating set { defaults.set(newValue.isFinite ? min(max(newValue, 180), 300) : 240, forKey: "starHeight") }
    }
}
