import SwiftUI
import UIKit

struct SpeakView: View {
    @EnvironmentObject private var app: AppState
    @EnvironmentObject private var speech: Speech
    @StateObject private var phrases = PhraseStore(bundle: .main)

    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 8)]

    var body: some View {
        VStack(spacing: 0) {
            stopButton
                .padding(.horizontal)
                .padding(.top, 12)
                .padding(.bottom, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if phrases.lastError != nil {
                        Text("Фразы пока не загрузились")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    if speech.voiceMissing {
                        Text("Голос не найден. Проверь: Настройки → Универсальный доступ → Устный контент → Голоса")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }

                    if !phrases.quick.isEmpty {
                        Text("Быстрые ответы").font(.headline)
                        LazyVGrid(columns: columns, spacing: 8) {
                            ForEach(phrases.quick, id: \.self) { phrase in
                                phraseButton(phrase)
                            }
                        }
                    }

                    ForEach(phrases.situations) { section in
                        sectionView(section)
                    }
                    ForEach(phrases.day) { section in
                        sectionView(section)
                    }
                }
                .padding()
            }
        }
    }

    private var stopButton: some View {
        Button {
            speech.stop()
        } label: {
            Text("Стоп")
                .font(.subheadline.bold())
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(Color(UIColor.tertiarySystemFill))
                .cornerRadius(10)
        }
        .accessibilityLabel("Остановить речь")
    }

    private func sectionView(_ section: PhraseStore.Section) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(section.name).font(.headline)
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(section.phrases, id: \.self) { phrase in
                    phraseButton(phrase)
                }
            }
        }
    }

    private func phraseButton(_ phrase: String) -> some View {
        Button {
            speech.speak(phrase)
            app.bigText = phrase
        } label: {
            Text(phrase)
                .font(.title3.bold())
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                .padding(8)
                .background(Color(UIColor.tertiarySystemFill))
                .cornerRadius(12)
        }
        .accessibilityLabel(phrase)
    }
}
