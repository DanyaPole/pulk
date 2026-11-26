import UIKit

final class SuggestionBar: UIView {
    var onPick: ((String) -> Void)?
    private let row = UIStackView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isAccessibilityElement = false
        clipsToBounds = true
        row.axis = .horizontal
        row.distribution = .fillEqually
        row.spacing = 4
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.topAnchor.constraint(equalTo: topAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    func show(_ words: [String]) {
        row.arrangedSubviews.forEach {
            row.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        for word in words.prefix(3) {
            let button = UIButton(type: .system)
            button.setTitle(word, for: .normal)
            button.titleLabel?.adjustsFontSizeToFitWidth = true
            button.titleLabel?.minimumScaleFactor = 0.5
            button.titleLabel?.lineBreakMode = .byTruncatingTail
            button.accessibilityLabel = "Слово: \(word)"
            button.backgroundColor = .tertiarySystemFill
            button.layer.cornerRadius = 8
            button.addTarget(self, action: #selector(picked(_:)), for: .touchUpInside)
            row.addArrangedSubview(button)
        }
    }

    @objc private func picked(_ sender: UIButton) {
        guard let word = sender.currentTitle, !word.isEmpty else { return }
        onPick?(word)
    }
}
