import JavaScriptCore
import XCTest

final class GoldenStrokesTests: XCTestCase {
    private var scriptError: String?

    func testGoldenStrokes() throws {
        let context = try makeContext()
        let api = context.evaluateScript("""
        (function () {
          var c = this.PulkCore;
          if (!c) return "PulkCore missing";
          var names = ["resolve", "spot", "jabPetal", "Words", "Jabs"];
          for (var i = 0; i < names.length; i++) {
            if (typeof c[names[i]] !== "function") return names[i] + " is " + typeof c[names[i]];
          }
          return "";
        })()
        """)
        if let scriptError {
            XCTFail(scriptError)
            return
        }
        XCTAssertEqual(api?.toString() ?? "api check failed", "")

        let url = try goldenURL()
        let data = try Data(contentsOf: url)
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let cases = root?["cases"] as? [[String: Any]] ?? []
        XCTAssertGreaterThanOrEqual(cases.count, 300, "cases.count=\(cases.count) in \(url.lastPathComponent)")
        if cases.count < 300 { return }

        let core = context.objectForKeyedSubscript("PulkCore")
        let tables = context.objectForKeyedSubscript("PULK")
        var mismatches: [String] = []
        for item in cases {
            if mismatches.count == 10 { break }
            let id = item["id"] as? String ?? "(no id)"
            let tableName = item["table"] as? String ?? ""
            let points = item["points"] as? [Any] ?? []
            guard let expect = item["expect"] as? [String: Any] else {
                mismatches.append("\(id) expect missing")
                continue
            }
            scriptError = nil
            let table = tables?.objectForKeyedSubscript(tableName)
            let result = core?.invokeMethod("resolve", withArguments: [table as Any, points])
            if let scriptError {
                mismatches.append("\(id) exception \(scriptError)")
                continue
            }
            guard let result, !result.isUndefined, !result.isNull else {
                mismatches.append("\(id) resolve returned nothing")
                continue
            }
            let gotPetal = jsInt(result.objectForKeyedSubscript("petal"))
            let gotKind = jsString(result.objectForKeyedSubscript("kind"))
            let gotV = jsString(result.objectForKeyedSubscript("v"))
            let expPetal = (expect["petal"] as? NSNumber)?.intValue
            let expKind = expect["kind"] as? String
            let expV = jsonString(expect["v"])
            if gotPetal != expPetal || gotKind != expKind || gotV != expV {
                mismatches.append("\(id) expected \(shape(expPetal, expKind, expV)) actual \(shape(gotPetal, gotKind, gotV))")
            }
        }
        if !mismatches.isEmpty {
            XCTFail(mismatches.joined(separator: "\n"))
        }
    }

    func testResolvePerformance() throws {
        let context = try makeContext()
        let table = try XCTUnwrap(context.objectForKeyedSubscript("PULK")?.objectForKeyedSubscript("RU"))
        let core = try XCTUnwrap(context.objectForKeyedSubscript("PulkCore"))
        let points: [Double] = [0, 0, 30, 10, 70, 20, 120, 30]
        measure {
            for _ in 0..<1000 {
                _ = core.invokeMethod("resolve", withArguments: [table, points])
            }
        }
    }

    private func makeContext() throws -> JSContext {
        let bundle = Bundle(for: GoldenStrokesTests.self)
        guard let context = JSContext() else {
            throw CoreLoadError("JSContext() failed")
        }
        context.exceptionHandler = { [weak self] _, value in
            self?.scriptError = value?.toString()
        }
        try eval(context, "var window = this; window.PULK = {};", name: "window")
        for name in ["data", "stopwords", "core"] {
            guard let url = bundle.url(forResource: name, withExtension: "js") else {
                throw CoreLoadError("missing \(name).js in the test bundle")
            }
            try eval(context, try String(contentsOf: url, encoding: .utf8), name: name)
        }
        return context
    }

    private func eval(_ context: JSContext, _ source: String, name: String) throws {
        scriptError = nil
        context.evaluateScript(source)
        if let scriptError {
            throw CoreLoadError("\(name): \(scriptError)")
        }
    }

    private func goldenURL() throws -> URL {
        let bundle = Bundle(for: GoldenStrokesTests.self)
        if let url = bundle.url(forResource: "strokes.golden", withExtension: "json") { return url }
        if let url = bundle.url(forResource: "strokes.golden", withExtension: "json", subdirectory: "shared") {
            return url
        }
        throw CoreLoadError("missing strokes.golden.json in the test bundle")
    }

    private func jsInt(_ value: JSValue?) -> Int? {
        guard let value, value.isNumber else { return nil }
        return Int(value.toInt32())
    }

    private func jsString(_ value: JSValue?) -> String? {
        guard let value, value.isString else { return nil }
        return value.toString()
    }

    private func shape(_ petal: Int?, _ kind: String?, _ v: String?) -> String {
        "petal=\(petal.map { String($0) } ?? "nil") kind=\(kind ?? "nil") v=\(v ?? "nil")"
    }

    private func jsonString(_ value: Any?) -> String? {
        guard let value, !(value is NSNull) else { return nil }
        return value as? String
    }
}

private struct CoreLoadError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}
