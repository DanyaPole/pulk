import SwiftUI
import UIKit

struct WriteView: View {
    @EnvironmentObject private var app: AppState
    @EnvironmentObject private var speech: Speech
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @State private var showClearConfirm = false

    private var leftHandedBinding: Binding<Bool> {
        Binding(
            get: { app.leftHanded },
            set: { app.leftHanded = $0 }
        )
    }

    private var starHeight: CGFloat {
        verticalSizeClass == .compact ? 150 : 260
    }

    var body: some View {
        VStack(spacing: 12) {
            ScrollView {
                VStack(spacing: 12) {
                    Text(app.text.isEmpty ? "Здесь появится набранный текст" : app.text)
                        .font(.body)
                        .frame(maxWidth: .infinity, minHeight: 120, alignment: .topLeading)
                        .padding(12)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(12)
                        .textSelection(.enabled)
                        .accessibilityLabel(app.text.isEmpty ? "Текст пуст" : app.text)

                    HStack(spacing: 8) {
                        actionButton("Пробел", label: "Пробел") { app.text.append(" ") }
                        actionButton("Стереть", label: "Стереть один знак") {
                            if !app.text.isEmpty { app.text.removeLast() }
                        }
                        actionButton(app.language, label: "Сменить язык") {
                            app.language = app.language == "RU" ? "EN" : "RU"
                        }
                    }
                    HStack(spacing: 8) {
                        actionButton("Сказать", label: "Сказать текст вслух") { speech.speak(app.text) }
                        actionButton("Крупно", label: "Показать текст крупно") {
                            let t = app.text.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !t.isEmpty { app.bigText = t }
                        }
                        actionButton("Копировать", label: "Скопировать текст") { copy() }
                        actionButton("Очистить", label: "Очистить текст") {
                            if !app.text.isEmpty { showClearConfirm = true }
                        }
                    }
                    actionButton("Стоп", label: "Остановить речь") { speech.stop() }

                    Toggle("Левая рука", isOn: leftHandedBinding)
                        .frame(minHeight: 48)
                    Text("В клавиатуре это включается отдельно")
                        .font(.footnote)
                        .foregroundColor(.secondary)

                    if app.copied {
                        Text("Скопировано")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    if speech.voiceMissing {
                        Text("Голос не найден. Проверь: Настройки → Универсальный доступ → Устный контент → Голоса")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                }
            }

            if app.bridge.isReady {
                StarHost(
                    bridge: app.bridge,
                    tableName: app.language,
                    leftHanded: app.leftHanded,
                    onStroke: handleStroke
                )
                .frame(height: starHeight)
            } else {
                Text("Звезда пока не загрузилась")
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .frame(height: starHeight)
            }
        }
        .padding()
        .alert("Очистить текст?", isPresented: $showClearConfirm) {
            Button("Да") { app.text = "" }
            Button("Оставить", role: .cancel) { }
        }
    }

    private func handleStroke(points: [Double], held: Bool) {
        guard let result = app.bridge.resolve(table: app.language, points: points) else { return }
        let value = held ? (app.bridge.alt(for: result.value) ?? result.value) : result.value
        app.text.append(value)
    }

    private func copy() {
        guard !app.text.isEmpty else { return }
        UIPasteboard.general.string = app.text
        app.copied = true
    }

    private func actionButton(_ title: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(Color(UIColor.tertiarySystemFill))
                .cornerRadius(10)
        }
        .accessibilityLabel(label)
    }
}
