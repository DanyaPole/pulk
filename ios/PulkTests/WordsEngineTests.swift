import JavaScriptCore
import XCTest

final class WordsEngineTests: XCTestCase {
    private var scriptError: String?

    func testRussianSuggestions() throws {
        let context = try makeContext()
        let dict = try resource("dict_ru", "txt")
        let next = try resource("next_ru", "txt")
        let loaded = context.objectForKeyedSubscript("__pulkLoadWords")?.call(withArguments: [dict, next, "RU"])
        if let scriptError { XCTFail(scriptError); return }
        XCTAssertEqual(loaded?.toBool(), true)

        let suggested = strings(context.objectForKeyedSubscript("__pulkSuggest")?.call(withArguments: ["при", 3]))
        XCTAssertGreaterThanOrEqual(suggested.count, 1)
        for word in suggested {
            XCTAssertTrue(word.lowercased().hasPrefix("при"), word)
        }

        let stopReport = context.evaluateScript("""
        (function () {
          var stop = this.PulkCore.Stop(this.PULK_STOP);
          var words = this.PULK_STOP && this.PULK_STOP.words;
          if (!words || typeof words.length !== "number" || words.length < 3) return "PULK_STOP.words is missing or shorter than 3";
          function scan(list) {
            if (!list || !list.length) return "";
            for (var i = 0; i < list.length; i++) if (stop(list[i])) return "stop";
            return "";
          }
          if (scan(this.__pulkSuggest("при", 8))) return "suggest(при) offered a stopped word";
          for (var s = 0; s < 3; s++) {
            var sample = String(words[s]);
            var prefix = sample.slice(0, Math.min(4, sample.length));
            if (scan(this.__pulkSuggest(prefix, 8))) return "suggest offered a stopped word";
          }
          return "";
        })()
        """)
        if let scriptError { XCTFail(scriptError); return }
        XCTAssertEqual(stopReport?.toString() ?? "stop check failed", "")

        let following = strings(context.objectForKeyedSubscript("__pulkNext")?.call(withArguments: ["я", 3]))
        XCTAssertGreaterThanOrEqual(following.count, 1)
    }

    func testWordsBuildPerformance() throws {
        let context = try makeContext()
        let dict = try resource("dict_ru", "txt")
        let next = try resource("next_ru", "txt")
        let load = try XCTUnwrap(context.objectForKeyedSubscript("__pulkLoadWords"))
        measure {
            _ = load.call(withArguments: [dict, next, "RU"])
            context.evaluateScript("this.__words = null;")
        }
    }

    private func makeContext() throws -> JSContext {
        let bundle = Bundle(for: WordsEngineTests.self)
        guard let context = JSContext() else { throw CoreLoadError("JSContext() failed") }
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
        try eval(context, Self.wordsHelpers, name: "wordsHelpers")
        return context
    }

    private func resource(_ name: String, _ ext: String) throws -> String {
        let bundle = Bundle(for: WordsEngineTests.self)
        guard let url = bundle.url(forResource: name, withExtension: ext) else {
            throw CoreLoadError("missing \(name).\(ext) in the test bundle")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    private func eval(_ context: JSContext, _ source: String, name: String) throws {
        scriptError = nil
        context.evaluateScript(source)
        if let scriptError { throw CoreLoadError("\(name): \(scriptError)") }
    }

    private func strings(_ value: JSValue?) -> [String] {
        guard let value, value.isArray else { return [] }
        let count = max(0, Int(value.objectForKeyedSubscript("length").toInt32()))
        return (0..<count).compactMap { index in
            guard let item = value.atIndex(index), item.isString else { return nil }
            return item.toString()
        }
    }

    // Duplicated from PulkWordsJS.helpers in ios/PulkKeyboard/PulkBridge+Words.swift.
    private static let wordsHelpers = """
    function __pulkLoadWords(dictText, nextText, tableName) {
      try {
        this.__words = null;
        var core = this.PulkCore;
        var table = this.PULK[tableName];
        if (!core || !table || typeof core.Words !== "function" || typeof core.Stop !== "function") return false;
        var bad = core.Stop(this.PULK_STOP);
        var words = new core.Words(String(dictText).split(/\\r?\\n/), table, bad);
        words.loadNext(String(nextText).split(/\\r?\\n/));
        this.__words = words;
        return true;
      } catch (e) {
        this.__words = null;
        return false;
      }
    }
    function __pulkSuggest(prefix, count) {
      try {
        var words = this.__words;
        if (!words || typeof words.suggest !== "function") return [];
        var n = count | 0;
        if (n < 1) return [];
        var list = words.suggest(String(prefix), n, {});
        return list || [];
      } catch (e) {
        return [];
      }
    }
    function __pulkNext(prev, count) {
      try {
        var words = this.__words;
        if (!words || typeof words.next !== "function") return [];
        var n = count | 0;
        if (n < 1) return [];
        var list = words.next(String(prev), n, true);
        return list || [];
      } catch (e) {
        return [];
      }
    }
    function __pulkUnloadWords() {
      this.__words = null;
      return true;
    }
    """
}

private struct CoreLoadError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}
