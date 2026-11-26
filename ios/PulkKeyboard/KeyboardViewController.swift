import UIKit

final class KeyboardViewController: UIInputViewController {
    private let bridge = PulkBridge(bundle: .main)
    private let settings = KeyboardSettings()
    private var star: StarView!
    private var globe: UIButton!
    private var shiftButton: UIButton!
    private var languageButton: UIButton!
    private var symbolsButton: UIButton!
    private var enterButton: UIButton!
    private var starHeight: NSLayoutConstraint!
    private var keyboardHeight: NSLayoutConstraint!
    private var repeatTimer: Timer?
    private var failureMessage: UILabel!
    private var lastKeyboardType: UIKeyboardType?
    private var hasKeyboardType = false
    private var language = "RU"
    private var shifted = false
    private var symbols = false
    private var secure = false

    override func viewDidLoad() {
        super.viewDidLoad()
        star = StarView(bridge: bridge)
        star.onStroke = { [weak self] points, held in self?.insertStroke(points, held: held) }
        globe = makeButton("", label: "Следующая клавиатура")
        globe.setImage(UIImage(systemName: "globe"), for: .normal)
        globe.accessibilityLabel = "Следующая клавиатура"
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        shiftButton = makeButton("⇧", label: "Заглавная буква")
        shiftButton.addTarget(self, action: #selector(toggleShift), for: .touchUpInside)
        languageButton = makeButton("RU", label: "Сменить язык")
        languageButton.addTarget(self, action: #selector(toggleLanguage), for: .touchUpInside)
        symbolsButton = makeButton("?123", label: "Буквы и символы")
        symbolsButton.addTarget(self, action: #selector(toggleSymbols), for: .touchUpInside)
        let space = makeButton("пробел", label: "Пробел")
        space.addTarget(self, action: #selector(insertSpace), for: .touchUpInside)
        let back = makeButton("⌫", label: "Удалить")
        back.addTarget(self, action: #selector(startDeleting), for: .touchDown)
        back.addTarget(self, action: #selector(stopDeleting), for: [.touchUpInside, .touchUpOutside, .touchCancel, .touchDragExit])
        enterButton = makeButton("Ввод", label: "Ввод")
        enterButton.addTarget(self, action: #selector(insertReturn), for: .touchUpInside)
        let row = UIStackView(arrangedSubviews: [globe, shiftButton, languageButton, symbolsButton])
        let row2 = UIStackView(arrangedSubviews: [space, back, enterButton])
        for bar in [row, row2] {
            bar.axis = .horizontal
            bar.distribution = .fillEqually
            bar.spacing = 4
            bar.heightAnchor.constraint(equalToConstant: 48).isActive = true
        }
        let stack = UIStackView(arrangedSubviews: [star, row, row2])
        stack.axis = .vertical
        stack.spacing = stackSpacing
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        starHeight = star.heightAnchor.constraint(equalToConstant: 240)
        starHeight.priority = .defaultHigh
        keyboardHeight = view.heightAnchor.constraint(equalToConstant: 344)
        keyboardHeight.priority = UILayoutPriority(999)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            stack.topAnchor.constraint(equalTo: view.topAnchor),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            starHeight, keyboardHeight
        ])
        failureMessage = UILabel()
        failureMessage.text = "Пульк не загрузился. Переключи клавиатуру и вернись."
        failureMessage.font = .preferredFont(forTextStyle: .footnote)
        failureMessage.adjustsFontSizeToFitWidth = true
        failureMessage.minimumScaleFactor = 0.6
        failureMessage.textAlignment = .center
        failureMessage.textColor = .label
        failureMessage.translatesAutoresizingMaskIntoConstraints = false
        star.addSubview(failureMessage)
        NSLayoutConstraint.activate([
            failureMessage.leadingAnchor.constraint(equalTo: star.leadingAnchor, constant: 8),
            failureMessage.trailingAnchor.constraint(equalTo: star.trailingAnchor, constant: -8),
            failureMessage.centerYAnchor.constraint(equalTo: star.centerYAnchor)
        ])
        updateTable()
        refreshTraits()
    }
    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        globe.isHidden = !needsInputModeSwitchKey
        let height: CGFloat = traitCollection.verticalSizeClass == .compact ? 110 : CGFloat(settings.starHeight)
        starHeight.constant = height
        keyboardHeight.constant = height + CGFloat(48 * 2) + stackSpacing * 2 + view.safeAreaInsets.bottom
        star.leftHanded = settings.leftHanded
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopDeleting()
        star.clearTouch()
    }
    deinit { stopDeleting() }
    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        guard isViewLoaded else { return }
        refreshTraits()
    }
    override func selectionDidChange(_ textInput: UITextInput?) {
        super.selectionDidChange(textInput)
        guard isViewLoaded else { return }
        refreshTraits()
    }
    private let stackSpacing: CGFloat = 4
    private func refreshTraits() {
        secure = textDocumentProxy.isSecureTextEntry == true
        star.secure = secure
        let keyboardType = textDocumentProxy.keyboardType
        if !hasKeyboardType || keyboardType != lastKeyboardType {
            switch (textDocumentProxy.keyboardType ?? .default) {
            case .numberPad, .phonePad, .decimalPad, .numbersAndPunctuation: symbols = true
            default: symbols = false
            }
            lastKeyboardType = keyboardType
            hasKeyboardType = true
        }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        switch (textDocumentProxy.autocapitalizationType ?? .none) {
        case .allCharacters: shifted = true
        case .words: shifted = before.isEmpty || before.last?.isWhitespace == true
        case .sentences:
            shifted = before.isEmpty || before.hasSuffix(". ") || before.hasSuffix("! ") || before.hasSuffix("? ") || before.hasSuffix("\n")
        default: shifted = false
        }
        let title: String
        switch (textDocumentProxy.returnKeyType ?? .default) {
        case .search: title = "Поиск"
        case .go: title = "Перейти"
        case .send: title = "Отправить"
        case .done: title = "Готово"
        default: title = "Ввод"
        }
        enterButton.setTitle(title, for: .normal)
        enterButton.accessibilityLabel = title
        globe.isHidden = !needsInputModeSwitchKey
        updateTable()
    }
    private func makeButton(_ title: String, label: String) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.adjustsFontSizeToFitWidth = true
        button.accessibilityLabel = label
        button.backgroundColor = .tertiarySystemFill
        button.layer.cornerRadius = 8
        return button
    }
    private func updateTable() {
        star.tableName = symbols ? "SYM" : language
        star.accessibilityLabel = symbols ? "Звезда символов Пульк" : "Звезда букв Пульк"
        star.secure = secure
        failureMessage.isHidden = bridge.isReady
        star.isAccessibilityElement = bridge.isReady
        star.shifted = shifted && !symbols
        languageButton.setTitle(language, for: .normal)
        symbolsButton.setTitle(symbols ? (language == "RU" ? "абв" : "abc") : "?123", for: .normal)
        shiftButton.isSelected = shifted
        shiftButton.setTitle(shifted ? "⇧ ✓" : "⇧", for: .normal)
        shiftButton.accessibilityValue = shifted ? "включено" : "выключено"
    }
    private func insertStroke(_ points: [Double], held: Bool) {
        guard let result = bridge.resolve(table: symbols ? "SYM" : language, points: points) else { return }
        let value = held ? (bridge.alt(for: result.value) ?? result.value) : result.value
        textDocumentProxy.insertText(shifted && !symbols ? value.uppercased() : value)
        if result.petal >= 0 {
            if (textDocumentProxy.autocapitalizationType ?? .none) != .allCharacters { shifted = false }
            updateTable()
        } else {
            refreshTraits()
        }
    }
    @objc private func toggleShift() { shifted.toggle(); updateTable() }
    @objc private func toggleLanguage() { language = language == "RU" ? "EN" : "RU"; symbols = false; updateTable() }
    @objc private func toggleSymbols() { symbols.toggle(); updateTable() }
    @objc private func insertSpace() {
        textDocumentProxy.insertText(" ")
        refreshTraits()
    }
    @objc private func insertReturn() {
        textDocumentProxy.insertText("\n")
        refreshTraits()
    }
    @objc private func startDeleting() {
        stopDeleting()
        textDocumentProxy.deleteBackward()
        refreshTraits()
        let timer = Timer(timeInterval: 0.45, repeats: false) { [weak self] _ in self?.beginRepeating() }
        repeatTimer = timer
        RunLoop.main.add(timer, forMode: .common)
    }
    @objc private func beginRepeating() {
        textDocumentProxy.deleteBackward()
        refreshTraits()
        repeatTimer?.invalidate()
        let timer = Timer(timeInterval: 0.07, repeats: true) { [weak self] _ in self?.repeatDelete() }
        repeatTimer = timer
        RunLoop.main.add(timer, forMode: .common)
    }
    @objc private func repeatDelete() {
        textDocumentProxy.deleteBackward()
        refreshTraits()
    }
    @objc private func stopDeleting() { repeatTimer?.invalidate(); repeatTimer = nil }
}
