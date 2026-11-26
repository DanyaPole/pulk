import Combine
import Foundation
import JavaScriptCore

final class PhraseStore: ObservableObject {
    struct Section: Identifiable {
        let id = UUID()
        let name: String
        let phrases: [String]
    }

    @Published private(set) var quick: [String] = []
    @Published private(set) var situations: [Section] = []
    @Published private(set) var day: [Section] = []
    @Published private(set) var lastError: String?

    init(bundle: Bundle) {
        guard let url = bundle.url(forResource: "data", withExtension: "js"),
              let script = try? String(contentsOf: url, encoding: .utf8) else {
            lastError = "Нет data.js в приложении"
            return
        }
        guard let context = JSContext() else {
            lastError = "JavaScriptCore недоступен"
            return
        }
        context.evaluateScript("var window = this;")
        context.evaluateScript(script)
        guard let pulk = context.objectForKeyedSubscript("PULK"), !pulk.isUndefined else {
            lastError = "PULK не найден в data.js"
            return
        }
        quick = Self.strings(pulk.objectForKeyedSubscript("quick"))
        situations = Self.sections(pulk.objectForKeyedSubscript("situations"), phrasesIndex: 1)
        day = Self.sections(pulk.objectForKeyedSubscript("day"), phrasesIndex: 2)
    }

    private static func strings(_ value: JSValue?) -> [String] {
        guard let array = value?.toArray() else { return [] }
        return array.compactMap { $0 as? String }
    }

    private static func sections(_ value: JSValue?, phrasesIndex: Int) -> [Section] {
        guard let array = value?.toArray() else { return [] }
        var out: [Section] = []
        for item in array {
            guard let row = item as? [Any], row.count > phrasesIndex,
                  let name = row[0] as? String,
                  let phrases = row[phrasesIndex] as? [String] else { continue }
            out.append(Section(name: name, phrases: phrases))
        }
        return out
    }
}
