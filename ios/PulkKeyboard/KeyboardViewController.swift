import UIKit

final class KeyboardViewController: UIInputViewController {
    private let bridge = PulkBridge(bundle: .main)
    private let settings = KeyboardSettings()
    private var star: StarView!
    private var stage: UIView!
    private var probe: ProbePanel!
    private var suggestionBar: SuggestionBar!
    private var globe: UIButton!
    private var shiftButton: UIButton!
    private var languageButton: UIButton!
    private var languageHold: UILongPressGestureRecognizer!
    private var symbolsButton: UIButton!
    private var enterButton: UIButton!
    private var starHeight: NSLayoutConstraint!
    private var suggestionHeight: NSLayoutConstraint!
    private var keyboardHeight: NSLayoutConstraint!
    private var repeatTimer: Timer?
    private var failureMessage: UILabel!
    private var lastKeyboardType: UIKeyboardType?
    private var hasKeyboardType = false
    private var language = "RU"
    private var shifted = false
    private var symbols = false
    private var secure = false
    private var probeOpen = false
    private var holdOpenedProbe = false
    private var loadingLanguage: String?
    private var failedLanguage: String?
    private var loadToken = 0
    private var wordsSuspended = false
    private var lastBefore: String?
    private var spaceButton: UIButton!
    private var backButton: UIButton!
    private var controlRow: UIStackView!
    private var controlRow2: UIStackView!
    private var controlsCompact: Bool?

    override func viewDidLoad() {
        super.viewDidLoad()
        star = StarView(bridge: bridge)
        star.onStroke = { [weak self] points, held in self?.insertStroke(points, held: held) }
        probe = ProbePanel()
        probe.isHidden = true
        probe.onBack = { [weak self] in self?.setProbe(open: false) }
        stage = UIView()
        stage.translatesAutoresizingMaskIntoConstraints = false
        star.translatesAutoresizingMaskIntoConstraints = false
        probe.translatesAutoresizingMaskIntoConstraints = false
        stage.addSubview(star)
        stage.addSubview(probe)
        suggestionBar = SuggestionBar()
        suggestionBar.onPick = { [weak self] word in self?.insertSuggestion(word) }
        globe = makeButton("", label: "Следующая клавиатура")
        globe.setImage(UIImage(systemName: "globe"), for: .normal)
        globe.accessibilityLabel = "Следующая клавиатура"
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        shiftButton = makeButton("⇧", label: "Заглавная буква")
        shiftButton.addTarget(self, action: #selector(toggleShift), for: .touchUpInside)
        languageButton = makeButton("RU", label: "Сменить язык")
        languageButton.addTarget(self, action: #selector(languageDown), for: .touchDown)
        languageButton.addTarget(self, action: #selector(toggleLanguage), for: .touchUpInside)
        languageHold = UILongPressGestureRecognizer(target: self, action: #selector(languageHeld(_:)))
        languageHold.minimumPressDuration = 0.8
        languageHold.cancelsTouchesInView = true
        languageButton.addGestureRecognizer(languageHold)
        symbolsButton = makeButton("?123", label: "Буквы и символы")
        symbolsButton.addTarget(self, action: #selector(toggleSymbols), for: .touchUpInside)
        spaceButton = makeButton("пробел", label: "Пробел")
        spaceButton.addTarget(self, action: #selector(insertSpace), for: .touchUpInside)
        backButton = makeButton("⌫", label: "Удалить")
        backButton.addTarget(self, action: #selector(startDeleting), for: .touchDown)
        backButton.addTarget(self, action: #selector(stopDeleting), for: [.touchUpInside, .touchUpOutside, .touchCancel, .touchDragExit])
        enterButton = makeButton("Ввод", label: "Ввод")
        enterButton.addTarget(self, action: #selector(insertReturn), for: .touchUpInside)
        controlRow = UIStackView(arrangedSubviews: [globe, shiftButton, languageButton, symbolsButton])
        controlRow2 = UIStackView(arrangedSubviews: [spaceButton, backButton, enterButton])
        for bar in [controlRow!, controlRow2!] {
            bar.axis = .horizontal
            bar.distribution = .fillEqually
            bar.spacing = 4
            bar.heightAnchor.constraint(equalToConstant: 48).isActive = true
        }
        let stack = UIStackView(arrangedSubviews: [suggestionBar, stage, controlRow, controlRow2])
        stack.axis = .vertical
        stack.spacing = stackSpacing
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        starHeight = stage.heightAnchor.constraint(equalToConstant: 240)
        starHeight.priority = .defaultHigh
        suggestionHeight = suggestionBar.heightAnchor.constraint(equalToConstant: 48)
        suggestionHeight.priority = UILayoutPriority(999)
        keyboardHeight = view.heightAnchor.constraint(equalToConstant: 396)
        keyboardHeight.priority = UILayoutPriority(999)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            stack.topAnchor.constraint(equalTo: view.topAnchor),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            star.leadingAnchor.constraint(equalTo: stage.leadingAnchor),
            star.trailingAnchor.constraint(equalTo: stage.trailingAnchor),
            star.topAnchor.constraint(equalTo: stage.topAnchor),
            star.bottomAnchor.constraint(equalTo: stage.bottomAnchor),
            probe.leadingAnchor.constraint(equalTo: stage.leadingAnchor),
            probe.trailingAnchor.constraint(equalTo: stage.trailingAnchor),
            probe.topAnchor.constraint(equalTo: stage.topAnchor),
            probe.bottomAnchor.constraint(equalTo: stage.bottomAnchor),
            starHeight, suggestionHeight, keyboardHeight
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
        refreshTraits()
    }
    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        globe.isHidden = !needsInputModeSwitchKey
        let compact = traitCollection.verticalSizeClass == .compact
        layoutControlRows(compact: compact)
        let height: CGFloat = compact ? 110 : CGFloat(settings.starHeight)
        starHeight.constant = height
        let showBar = !compact && !secure
        suggestionHeight.constant = showBar ? 48 : 0
        suggestionBar.isHidden = !showBar
        if compact {
            keyboardHeight.constant = height + 48 + stackSpacing + view.safeAreaInsets.bottom
        } else {
            let gaps: CGFloat = showBar ? 3 : 2
            keyboardHeight.constant = height + suggestionHeight.constant + CGFloat(48 * 2) + stackSpacing * gaps + view.safeAreaInsets.bottom
        }
        star.leftHanded = settings.leftHanded
    }
    private func layoutControlRows(compact: Bool) {
        guard let controlRow, let controlRow2, let globe, let shiftButton, let languageButton, let symbolsButton, let spaceButton, let backButton, let enterButton else { return }
        if controlsCompact == compact { return }
        controlsCompact = compact
        let buttons = [globe, shiftButton, languageButton, symbolsButton, spaceButton, backButton, enterButton]
        for button in buttons {
            (button.superview as? UIStackView)?.removeArrangedSubview(button)
            button.removeFromSuperview()
        }
        if compact {
            for button in buttons { controlRow.addArrangedSubview(button) }
            controlRow2.isHidden = true
        } else {
            controlRow.addArrangedSubview(globe)
            controlRow.addArrangedSubview(shiftButton)
            controlRow.addArrangedSubview(languageButton)
            controlRow.addArrangedSubview(symbolsButton)
            controlRow2.addArrangedSubview(spaceButton)
            controlRow2.addArrangedSubview(backButton)
            controlRow2.addArrangedSubview(enterButton)
            controlRow2.isHidden = false
        }
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopDeleting()
        star.clearTouch()
        probe.stopSpeech()
    }
    deinit { repeatTimer?.invalidate() }
    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        wordsSuspended = true
        loadToken += 1
        loadingLanguage = nil
        failedLanguage = nil
        bridge.unloadWords()
        if isViewLoaded, suggestionBar != nil { suggestionBar.show([]) }
        if probeOpen { probe.showStatus(language: nil) }
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        guard isViewLoaded else { return }
        if previousTraitCollection?.verticalSizeClass != traitCollection.verticalSizeClass {
            refreshSuggestions()
        }
    }
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
        if before != lastBefore {
            switch (textDocumentProxy.autocapitalizationType ?? .none) {
            case .allCharacters: shifted = true
            case .words: shifted = before.isEmpty || before.last?.isWhitespace == true
            case .sentences:
                shifted = before.isEmpty || before.hasSuffix(". ") || before.hasSuffix("! ") || before.hasSuffix("? ") || before.hasSuffix("\n") || before.hasSuffix("… ")
            default: shifted = false
            }
            lastBefore = before
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
        refreshSuggestions()
    }
    private func refreshSuggestions() {
        view.setNeedsLayout()
        if !secure && !symbols && traitCollection.verticalSizeClass != .compact { ensureWords() }
        let showWords = !secure && traitCollection.verticalSizeClass != .compact && !symbols && bridge.wordsLanguage == language.lowercased()
        if showWords {
            let before = textDocumentProxy.documentContextBeforeInput ?? ""
            let parts = WordContext.parts(before)
            let allCaps = (textDocumentProxy.autocapitalizationType ?? .none) == .allCharacters
            let raw: [String]
            if parts.prefix.isEmpty {
                raw = bridge.nextWords(after: parts.previous, count: 3)
            } else {
                let folded = WordContext.fold(parts.prefix)
                raw = Array(bridge.suggestions(prefix: parts.prefix, count: 4).filter { WordContext.fold($0) != folded }.prefix(3))
            }
            suggestionBar.show(raw.map { WordContext.shaped($0, prefix: parts.prefix, shift: shifted, allCaps: allCaps) })
        } else {
            suggestionBar.show([])
        }
        if probeOpen { probe.showStatus(language: bridge.wordsLanguage) }
    }
    private func ensureWords() {
        if wordsSuspended { return }
        let code = language.lowercased()
        if bridge.wordsLanguage == code || loadingLanguage == code || failedLanguage == code { return }
        loadingLanguage = code
        loadToken += 1
        let token = loadToken
        bridge.loadWords(language: code) { [weak self] ok in
            guard let self, self.loadToken == token else { return }
            self.loadingLanguage = nil
            if ok {
                self.failedLanguage = nil
            } else {
                self.failedLanguage = code
            }
            self.refreshSuggestions()
        }
    }
    private func setProbe(open: Bool) {
        probeOpen = open
        probe.isHidden = !open
        star.isHidden = open
        if open {
            star.clearTouch()
            probe.showStatus(language: bridge.wordsLanguage)
        } else {
            probe.stopSpeech()
        }
    }
    private func insertSuggestion(_ word: String) {
        guard !secure else { return }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let prefix = WordContext.parts(before).prefix
        for _ in prefix { textDocumentProxy.deleteBackward() }
        let lead = prefix.isEmpty && !before.isEmpty && before.last?.isWhitespace != true
        textDocumentProxy.insertText((lead ? " " : "") + word + " ")
        if (textDocumentProxy.autocapitalizationType ?? .none) != .allCharacters { shifted = false }
        refreshTraits()
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
    @objc private func languageDown() {
        holdOpenedProbe = false
    }
    @objc private func languageHeld(_ gesture: UILongPressGestureRecognizer) {
        if gesture.state == .ended || gesture.state == .cancelled || gesture.state == .failed {
            holdOpenedProbe = false
            return
        }
        guard gesture.state == .began else { return }
        holdOpenedProbe = true
        setProbe(open: true)
    }
    @objc private func toggleLanguage() {
        if holdOpenedProbe {
            holdOpenedProbe = false
            return
        }
        language = language == "RU" ? "EN" : "RU"
        symbols = false
        wordsSuspended = false
        failedLanguage = nil
        loadingLanguage = nil
        loadToken += 1
        updateTable()
    }
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
        repeatTimer?.invalidate()
        let timer = Timer(timeInterval: 0.07, repeats: true) { [weak self] _ in self?.repeatDelete() }
        repeatTimer = timer
        RunLoop.main.add(timer, forMode: .common)
    }
    @objc private func repeatDelete() {
        textDocumentProxy.deleteBackward()
    }
    @objc private func stopDeleting() {
        repeatTimer?.invalidate()
        repeatTimer = nil
        refreshTraits()
    }
}

private enum WordContext {
    static func parts(_ before: String) -> (prefix: String, previous: String) {
        let chars = Array(before)
        var index = chars.count
        while index > 0, chars[index - 1].isLetter { index -= 1 }
        let prefix = String(chars[index...])
        if !prefix.isEmpty { return (prefix, "") }
        var cursor = chars.count
        var sentence = false
        while cursor > 0, !chars[cursor - 1].isLetter {
            if ".!?…".contains(chars[cursor - 1]) { sentence = true }
            cursor -= 1
        }
        if cursor == 0 || sentence { return ("", "^") }
        let end = cursor
        while cursor > 0, chars[cursor - 1].isLetter { cursor -= 1 }
        return ("", fold(String(chars[cursor..<end])))
    }

    static func fold(_ text: String) -> String {
        text.lowercased().replacingOccurrences(of: "ё", with: "е")
    }

    static func shaped(_ word: String, prefix: String, shift: Bool, allCaps: Bool) -> String {
        if allCaps { return word.uppercased() }
        if prefix.count > 1, prefix == prefix.uppercased(), prefix != prefix.lowercased() {
            return word.uppercased()
        }
        if shift || prefix.first?.isUppercase == true {
            guard let first = word.first else { return word }
            return String(first).uppercased() + word.dropFirst()
        }
        return word
    }
}
