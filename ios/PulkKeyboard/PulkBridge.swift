import Foundation
import JavaScriptCore

final class PulkBridge {
    private let context: JSContext
    private var tables: [String: [[String]]] = [:]
    private(set) var lastError: String?
    private(set) var isReady = false
    private(set) var ph: [Double] = []
    private(set) var radius: Double = 0
    private(set) var holdMilliseconds: Double = 0
    private(set) var spots: [[[Double]]] = []

    init(bundle: Bundle) {
        context = JSContext()!
        context.exceptionHandler = { [weak self] _, exception in
            self?.lastError = exception?.toString() ?? "JavaScript exception"
            self?.isReady = false
        }
        context.evaluateScript("var window = this; window.PULK = {};")
        for name in ["data", "stopwords", "core"] {
            guard let url = bundle.url(forResource: name, withExtension: "js") else {
                if name == "stopwords" { continue }
                lastError = "Missing \(name).js"
                return
            }
            guard let script = try? String(contentsOf: url, encoding: .utf8) else {
                lastError = "Cannot read \(name).js"
                return
            }
            context.evaluateScript(script)
            if lastError != nil { return }
        }
        guard let core = context.objectForKeyedSubscript("PulkCore"), !core.isUndefined,
              let phValue = core.objectForKeyedSubscript("PH"),
              let r = core.objectForKeyedSubscript("R"), r.isNumber,
              let hold = core.objectForKeyedSubscript("HOLD_MS"), hold.isNumber else {
            lastError = "Missing core geometry"
            return
        }
        ph = Self.numbers(phValue)
        radius = r.toDouble()
        holdMilliseconds = hold.toDouble()
        guard ph.count == 2, ph.allSatisfy({ $0.isFinite }), radius.isFinite, radius > 0, holdMilliseconds.isFinite, holdMilliseconds >= 0 else {
            lastError = "Invalid core geometry"
            return
        }
        for petal in 0..<8 {
            var row: [[Double]] = []
            for kind in ["c", "l", "r", "b"] {
                guard let value = core.invokeMethod("spot", withArguments: [petal, kind]) else {
                    lastError = "Missing spot"
                    return
                }
                let point = Self.numbers(value)
                guard point.count == 2, point.allSatisfy({ $0.isFinite }) else {
                    lastError = "Invalid spot"
                    return
                }
                row.append(point)
            }
            spots.append(row)
        }
        isReady = lastError == nil
    }

    private static func numbers(_ value: JSValue) -> [Double] {
        (value.toArray() ?? []).compactMap { ($0 as? NSNumber)?.doubleValue }
    }

    func table(_ name: String) -> [[String]] {
        if let cached = tables[name] { return cached }
        guard isReady, let value = context.objectForKeyedSubscript("PULK")?.objectForKeyedSubscript(name),
              let rows = value.toArray() as? [[String]], rows.count == 8,
              rows.allSatisfy({ $0.count == 4 }) else {
            lastError = "Invalid table: \(name)"
            return []
        }
        tables[name] = rows
        return rows
    }

    func resolve(table name: String, points: [Double]) -> (petal: Int, kind: String, value: String)? {
        guard isReady, points.count >= 2, points.count.isMultiple(of: 2), !table(name).isEmpty,
              let core = context.objectForKeyedSubscript("PulkCore"),
              let jsTable = context.objectForKeyedSubscript("PULK")?.objectForKeyedSubscript(name),
              let result = core.invokeMethod("resolve", withArguments: [jsTable, points]),
              let petal = result.objectForKeyedSubscript("petal"), petal.isNumber,
              let kind = result.objectForKeyedSubscript("kind"), kind.isString,
              let cell = result.objectForKeyedSubscript("v"), cell.isString,
              let kindString = kind.toString(), let value = cell.toString() else { return nil }
        return (Int(petal.toInt32()), kindString, value)
    }

    func alt(for value: String) -> String? {
        guard isReady, let alt = context.objectForKeyedSubscript("PulkCore")?.objectForKeyedSubscript("ALT")?.objectForKeyedSubscript(value),
              alt.isString else { return nil }
        return alt.toString()
    }
}
