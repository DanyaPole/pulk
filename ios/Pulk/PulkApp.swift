import SwiftUI

@main
struct PulkApp: App {
    @StateObject private var app = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(app).environmentObject(app.speech)
        }
    }
}

private struct ContentView: View {
    @EnvironmentObject private var app: AppState

    var body: some View {
        TabView {
            EnableView()
                .tabItem { Label("Включить", systemImage: "gearshape") }
            WriteView()
                .tabItem { Label("Писать", systemImage: "pencil") }
            SpeakView()
                .tabItem { Label("Говорить", systemImage: "bubble.left") }
        }
        .fullScreenCover(isPresented: Binding(
            get: { app.bigText != nil },
            set: { if !$0 { app.bigText = nil } }
        )) {
            BigTextView(text: app.bigText ?? "", onStop: { app.speech.stop() }, onClose: { app.bigText = nil })
        }
    }
}

private struct EnableView: View {
    @State private var sample = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Как включить Пульк").font(.largeTitle.bold())
                Text("1. Открой Настройки → Основные.")
                Text("2. Выбери Клавиатура → Клавиатуры.")
                Text("3. Нажми «Новая клавиатура» и выбери Пульк.")
                Text("Пульк работает без интернета и ничего не отправляет.")
                TextField("Попробуй клавиатуру здесь", text: $sample)
                    .textFieldStyle(.roundedBorder)
                    .frame(minHeight: 48)
                    .accessibilityLabel("Попробуй клавиатуру здесь")
            }
            .padding()
        }
    }
}
