import Foundation
import JavaScriptCore

// Same text as WordsEngineTests.wordsHelpers. PulkTests does not link this target, so the test copies it.
enum PulkWordsJS {
    static let helpers = """
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

extension PulkBridge {
    func loadWords(language: String, completion: @escaping (Bool) -> Void) {
        let lang = language.lowercased()
        guard lang == "ru" || lang == "en" else {
            finish(false, completion)
            return
        }
        let start = {
            self.wordsLoadGeneration += 1
            let generation = self.wordsLoadGeneration
            self.wordsLanguage = nil
            _ = self.guardingKeyboard {
                self.context.objectForKeyedSubscript("__pulkUnloadWords")?.call(withArguments: [])
            }
            let bundle = self.bundle
            DispatchQueue.global(qos: .userInitiated).async {
                let dict = Self.text(name: "dict_\(lang)", ext: "txt", bundle: bundle)
                let next = Self.text(name: "next_\(lang)", ext: "txt", bundle: bundle)
                DispatchQueue.main.async {
                    guard generation == self.wordsLoadGeneration else {
                        completion(false)
                        return
                    }
                    guard let dict, let next, self.installHelpers() else {
                        completion(false)
                        return
                    }
                    // Stall: Words() plus loadNext for one language, on the main queue, in this block.
                    let table = lang == "en" ? "EN" : "RU"
                    let value = self.guardingKeyboard {
                        self.context.objectForKeyedSubscript("__pulkLoadWords")?.call(withArguments: [dict, next, table])
                    }
                    let ok = value?.isBoolean == true && value?.toBool() == true
                    self.wordsLanguage = ok ? lang : nil
                    completion(ok)
                }
            }
        }
        if Thread.isMainThread {
            start()
        } else {
            DispatchQueue.main.async(execute: start)
        }
    }

    func suggestions(prefix: String, count: Int) -> [String] {
        guard Thread.isMainThread, wordsLanguage != nil, count > 0, !prefix.isEmpty else { return [] }
        let value = guardingKeyboard {
            context.objectForKeyedSubscript("__pulkSuggest")?.call(withArguments: [prefix, count])
        }
        return Self.strings(value)
    }

    func nextWords(after previous: String, count: Int) -> [String] {
        guard Thread.isMainThread, wordsLanguage != nil, count > 0 else { return [] }
        let value = guardingKeyboard {
            context.objectForKeyedSubscript("__pulkNext")?.call(withArguments: [previous, count])
        }
        return Self.strings(value)
    }

    func unloadWords() {
        let work = {
            self.wordsLoadGeneration += 1
            self.wordsLanguage = nil
            _ = self.guardingKeyboard {
                self.context.objectForKeyedSubscript("__pulkUnloadWords")?.call(withArguments: [])
            }
        }
        if Thread.isMainThread {
            work()
        } else {
            DispatchQueue.main.async(execute: work)
        }
    }

    private func installHelpers() -> Bool {
        if context.objectForKeyedSubscript("__pulkLoadWords")?.isUndefined == false { return true }
        _ = guardingKeyboard { context.evaluateScript(PulkWordsJS.helpers) }
        return context.objectForKeyedSubscript("__pulkLoadWords")?.isUndefined == false
    }

    private func guardingKeyboard(_ body: () -> JSValue?) -> JSValue? {
        let saved = context.exceptionHandler
        var thrown = false
        context.exceptionHandler = { _, _ in thrown = true }
        defer { context.exceptionHandler = saved }
        let value = body()
        return thrown ? nil : value
    }

    private func finish(_ ok: Bool, _ completion: @escaping (Bool) -> Void) {
        if Thread.isMainThread {
            completion(ok)
        } else {
            DispatchQueue.main.async { completion(ok) }
        }
    }

    private static func text(name: String, ext: String, bundle: Bundle) -> String? {
        guard let url = bundle.url(forResource: name, withExtension: ext) else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    private static func strings(_ value: JSValue?) -> [String] {
        guard let value, value.isArray else { return [] }
        let count = max(0, Int(value.objectForKeyedSubscript("length").toInt32()))
        var words: [String] = []
        words.reserveCapacity(count)
        for index in 0..<count {
            guard let item = value.atIndex(index), item.isString, let word = item.toString() else { continue }
            words.append(word)
        }
        return words
    }
}
