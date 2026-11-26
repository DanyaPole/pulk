import SwiftUI
import UIKit

struct BigTextView: View {
    let text: String
    let onStop: () -> Void
    let onClose: () -> Void

    var body: some View {
        GeometryReader { geo in
            let fontSize = Self.fittedSize(
                for: text,
                in: CGSize(width: max(1, geo.size.width - 96), height: max(1, geo.size.height - 170))
            )
            VStack(spacing: 12) {
                HStack(spacing: 8) {
                    Button(action: onStop) {
                        Text("Стоп")
                            .font(.subheadline.bold())
                            .frame(minWidth: 48, minHeight: 48)
                    }
                    .accessibilityLabel("Остановить речь")
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 30))
                            .frame(minWidth: 48, minHeight: 48)
                    }
                    .accessibilityLabel("Закрыть")
                }
                Spacer(minLength: 0)
                Text(text.isEmpty ? " " : text)
                    .font(.system(size: fontSize, weight: .bold))
                    .minimumScaleFactor(0.5)
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .padding(.horizontal, 24)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel(text)
                Spacer(minLength: 0)
                Text("Коснись в любом месте, чтобы закрыть")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(24)
        }
        .background(Color(UIColor.systemBackground).ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture(perform: onClose)
    }

    private static func fittedSize(for text: String, in bounds: CGSize) -> CGFloat {
        let measured = text.isEmpty ? " " : text
        var low: CGFloat = 12
        var high: CGFloat = 480
        for _ in 0..<20 {
            let mid = (low + high) / 2
            let font = UIFont.systemFont(ofSize: mid, weight: .bold)
            let style = NSMutableParagraphStyle()
            style.lineSpacing = 6
            let rect = (measured as NSString).boundingRect(
                with: CGSize(width: bounds.width, height: .greatestFiniteMagnitude),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: [.font: font, .paragraphStyle: style],
                context: nil
            )
            if rect.width <= bounds.width && rect.height <= bounds.height {
                low = mid
            } else {
                high = mid
            }
        }
        return low
    }
}
