import SwiftUI

@main
struct PulkApp: App {
    @State private var sample = ""

    var body: some Scene {
        WindowGroup {
            VStack(alignment: .leading, spacing: 24) {
                Text("Как включить Пульк").font(.largeTitle.bold())
                Text("1. Открой Настройки → Основные.")
                Text("2. Выбери Клавиатура → Клавиатуры.")
                Text("3. Нажми «Новая клавиатура» и выбери Пульк.")
                TextField("Попробуй клавиатуру здесь", text: $sample)
                    .textFieldStyle(.roundedBorder)
                    .frame(minHeight: 48)
                Spacer()
            }
            .padding()
        }
    }
}
