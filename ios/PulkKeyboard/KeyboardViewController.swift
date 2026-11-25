import UIKit
import JavaScriptCore

final class KeyboardViewController: UIInputViewController {
    private var star: StarView?
    private var language = "RU"
    private var shifted = false
    private var symbols = false
    private var context: JSContext?

    override func viewDidLoad() {
        super.viewDidLoad()
        let js = JSContext()
        js?.evaluateScript("var window = this; window.PULK = {};")
        for name in ["core", "data"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "js"),
               let script = try? String(contentsOf: url, encoding: .utf8) {
                js?.evaluateScript(script)
            }
        }
        context = js

        let starView = StarView()
        starView.backgroundColor = .secondarySystemBackground
        starView.context = js
        starView.onStroke = { [weak self] points in self?.insertStroke(points) }
        star = starView

        let globe = makeButton("🌐", label: "Следующая клавиатура")
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        let shift = makeButton("⇧", label: "Заглавная буква")
        shift.addTarget(self, action: #selector(toggleShift), for: .touchUpInside)
        let lang = makeButton("RU/EN", label: "Сменить язык")
        lang.addTarget(self, action: #selector(toggleLanguage), for: .touchUpInside)
        let sym = makeButton("?123", label: "Буквы и символы")
        sym.addTarget(self, action: #selector(toggleSymbols), for: .touchUpInside)
        let space = makeButton("пробел", label: "Пробел")
        space.addTarget(self, action: #selector(insertSpace), for: .touchUpInside)
        let back = makeButton("⌫", label: "Удалить")
        back.addTarget(self, action: #selector(deleteBackward), for: .touchUpInside)
        let enter = makeButton("⏎", label: "Ввод")
        enter.addTarget(self, action: #selector(insertReturn), for: .touchUpInside)
        let row = UIStackView(arrangedSubviews: [globe, shift, lang, sym])
        let row2 = UIStackView(arrangedSubviews: [space, back, enter])
        for bar in [row, row2] {
            bar.axis = .horizontal
            bar.distribution = .fillEqually
            bar.spacing = 4
            bar.heightAnchor.constraint(equalToConstant: 48).isActive = true
        }
        let stack = UIStackView(arrangedSubviews: [starView, row, row2])
        stack.axis = .vertical
        stack.spacing = 4
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            stack.topAnchor.constraint(equalTo: view.topAnchor),
            stack.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            starView.heightAnchor.constraint(equalToConstant: 240)
        ])
        updateTable()
    }

    private func makeButton(_ title: String, label: String) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.adjustsFontSizeToFitWidth = true
        button.accessibilityLabel = label
        return button
    }

    private func updateTable() {
        star?.tableName = symbols ? "SYM" : language
        star?.shifted = shifted && !symbols
        star?.setNeedsDisplay()
    }

    private func insertStroke(_ points: [Double]) {
        guard let js = context,
              let core = js.objectForKeyedSubscript("PulkCore"),
              let table = js.objectForKeyedSubscript("PULK")?.objectForKeyedSubscript(symbols ? "SYM" : language),
              let result = core.invokeMethod("resolve", withArguments: [table, points]),
              let value = result.objectForKeyedSubscript("v")?.toString() else { return }
        textDocumentProxy.insertText(shifted && !symbols ? value.uppercased() : value)
        shifted = false
        updateTable()
    }

    @objc private func toggleShift() { shifted.toggle(); updateTable() }
    @objc private func toggleLanguage() { language = language == "RU" ? "EN" : "RU"; symbols = false; updateTable() }
    @objc private func toggleSymbols() { symbols.toggle(); updateTable() }
    @objc private func insertSpace() { textDocumentProxy.insertText(" ") }
    @objc private func insertReturn() { textDocumentProxy.insertText("\n") }
    @objc private func deleteBackward() { textDocumentProxy.deleteBackward() }
}

private final class StarView: UIView {
    var context: JSContext?
    var tableName = "RU"
    var shifted = false
    var onStroke: (([Double]) -> Void)?
    private var points: [Double] = []
    private var activeTouch: UITouch?

    private var scale: CGFloat { min(bounds.width, bounds.height) / 210 }
    private var centerPoint: CGPoint { CGPoint(x: bounds.midX, y: bounds.midY) }

    private func corePoint(_ point: CGPoint) -> [Double] {
        let ph = context?.objectForKeyedSubscript("PulkCore")?.objectForKeyedSubscript("PH")?.toArray() as? [Double] ?? [0, 0]
        return [Double((point.x - centerPoint.x) / scale) + ph[0],
                Double((centerPoint.y - point.y) / scale) + ph[1]]
    }

    override func draw(_ rect: CGRect) {
        guard let js = context,
              let core = js.objectForKeyedSubscript("PulkCore"),
              let ph = core.objectForKeyedSubscript("PH")?.toArray() as? [Double],
              let radius = core.objectForKeyedSubscript("R")?.toDouble(),
              let table = js.objectForKeyedSubscript("PULK")?.objectForKeyedSubscript(tableName),
              let cg = UIGraphicsGetCurrentContext() else { return }
        let c = centerPoint
        for petal in 0..<8 {
            let path = UIBezierPath()
            path.move(to: c)
            for step in 0...12 {
                let angle = (Double(petal) * 45 - 22.5 + Double(step) * 45 / 12) * Double.pi / 180
                path.addLine(to: CGPoint(x: c.x + CGFloat(radius * cos(angle)) * scale,
                                         y: c.y - CGFloat(radius * sin(angle)) * scale))
            }
            path.close()
            UIColor.systemBackground.setFill()
            UIColor.separator.setStroke()
            path.fill()
            path.stroke()
            for (index, kind) in ["c", "l", "r", "b"].enumerated() {
                guard let pos = core.invokeMethod("spot", withArguments: [petal, kind])?.toArray() as? [Double],
                      let value = table.atIndex(petal)?.atIndex(index)?.toString(), !value.isEmpty else { continue }
                let label = shifted ? value.uppercased() : value
                let font = UIFont.systemFont(ofSize: index == 0 ? 19 : 12, weight: index == 0 ? .bold : .regular)
                let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.label]
                let size = (label as NSString).size(withAttributes: attrs)
                let x = c.x + CGFloat(pos[0] - ph[0]) * scale - size.width / 2
                let y = c.y - CGFloat(pos[1] - ph[1]) * scale - size.height / 2
                (label as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: attrs)
            }
        }
        cg.setStrokeColor(UIColor.separator.cgColor)
    }

    private func append(_ touch: UITouch) {
        points.append(contentsOf: corePoint(touch.location(in: self)))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard activeTouch == nil, let touch = touches.first else { return }
        activeTouch = touch
        points = []
        append(touch)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        append(touch)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        append(touch)
        onStroke?(points)
        activeTouch = nil
        points = []
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        activeTouch = nil
        points = []
    }
}
