import SwiftUI
import UIKit

struct StarHost: UIViewRepresentable {
    let bridge: PulkBridge
    var tableName: String
    var leftHanded: Bool
    var onStroke: ([Double], Bool) -> Void

    func makeUIView(context: Context) -> StarView {
        let star = StarView(bridge: bridge)
        star.tableName = tableName
        star.leftHanded = leftHanded
        star.onStroke = onStroke
        return star
    }

    func updateUIView(_ star: StarView, context: Context) {
        star.tableName = tableName
        star.leftHanded = leftHanded
        star.onStroke = onStroke
    }

    static func dismantleUIView(_ uiView: StarView, coordinator: ()) {
        uiView.clearTouch()
    }
}
