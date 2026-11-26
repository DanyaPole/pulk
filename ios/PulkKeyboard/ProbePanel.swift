import AVFoundation
import Darwin
import UIKit

final class ProbePanel: UIView {
    var onBack: (() -> Void)?
    private var speaker: AVSpeechSynthesizer?
    private let impact = UIImpactFeedbackGenerator(style: .medium)
    private let status = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .secondarySystemBackground
        isAccessibilityElement = false
        status.font = .preferredFont(forTextStyle: .body)
        status.adjustsFontForContentSizeCategory = true
        status.numberOfLines = 0
        status.textAlignment = .center
        status.textColor = .label
        let stack = UIStackView(arrangedSubviews: [
            status,
            makeButton("Вибрация", action: #selector(vibrate)),
            makeButton("Голос", action: #selector(speak)),
            makeButton("Объявление", action: #selector(announce)),
            makeButton("Назад", action: #selector(back))
        ])
        stack.axis = .vertical
        stack.spacing = 4
        stack.translatesAutoresizingMaskIntoConstraints = false
        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)
        addSubview(scroll)
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: trailingAnchor),
            scroll.topAnchor.constraint(equalTo: topAnchor),
            scroll.bottomAnchor.constraint(equalTo: bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 4),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -4),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 4),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -4),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -8)
        ])
        showStatus(language: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    func showStatus(language: String?) {
        let lang = language ?? "—"
        if let megabytes = Self.footprintMegabytes() {
            status.text = "Память: \(megabytes) МБ\nЯзык: \(lang)"
        } else {
            status.text = "Память: —\nЯзык: \(lang)"
        }
    }

    func stopSpeech() {
        if speaker?.isSpeaking == true { speaker?.stopSpeaking(at: .immediate) }
    }

    private func makeButton(_ title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.adjustsFontSizeToFitWidth = true
        button.titleLabel?.font = .preferredFont(forTextStyle: .body)
        button.backgroundColor = .tertiarySystemFill
        button.layer.cornerRadius = 8
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }

    @objc private func vibrate() {
        impact.prepare()
        impact.impactOccurred()
    }

    @objc private func speak() {
        if speaker?.isSpeaking == true { speaker?.stopSpeaking(at: .immediate) }
        if speaker == nil { speaker = AVSpeechSynthesizer() }
        let utterance = AVSpeechUtterance(string: "Проверка голоса")
        utterance.voice = AVSpeechSynthesisVoice(language: "ru-RU")
        speaker?.speak(utterance)
    }

    @objc private func announce() {
        UIAccessibility.post(notification: .announcement, argument: "Проверка объявления")
    }

    @objc private func back() {
        onBack?()
    }

    private static func footprintMegabytes() -> String? {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.stride / MemoryLayout<integer_t>.stride)
        let result = withUnsafeMutablePointer(to: &info) { pointer -> kern_return_t in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { rebound in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), rebound, &count)
            }
        }
        guard result == KERN_SUCCESS else { return nil }
        return String(format: "%.1f", Double(info.phys_footprint) / 1048576)
    }
}
