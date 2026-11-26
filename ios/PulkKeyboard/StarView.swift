import UIKit

final class StarView: UIView {
    let bridge: PulkBridge
    var onStroke: (([Double], Bool) -> Void)?
    var tableName = "RU" { didSet { if tableName != oldValue { cells = bridge.table(tableName); setNeedsDisplay() } } }
    var shifted = false { didSet { if shifted != oldValue { setNeedsDisplay() } } }
    var leftHanded = false {
        didSet {
            if leftHanded != oldValue {
                clearTouch()
                setNeedsDisplay()
                setNeedsLayout()
            }
        }
    }
    var secure = false {
        didSet {
            if secure {
                trail.path = nil
                highlight.path = nil
            }
        }
    }
    private var cells: [[String]] = []
    private let trail = CAShapeLayer()
    private let highlight = CAShapeLayer()
    private var points: [Double] = []
    private var activeTouch: UITouch?
    private var stillPoint = CGPoint.zero
    private var stillSince: TimeInterval = 0
    private var trailPath = UIBezierPath()
    private let pMin: Double = 16

    init(bridge: PulkBridge) {
        self.bridge = bridge
        super.init(frame: .zero)
        cells = bridge.table(tableName)
        isAccessibilityElement = true
        accessibilityLabel = "Звезда букв Пульк"
        accessibilityHint = "Проведи пальцем от центра к букве"
        accessibilityTraits = .allowsDirectInteraction
        backgroundColor = .secondarySystemBackground
        contentMode = .redraw
        for shape in [highlight, trail] {
            shape.fillColor = UIColor.clear.cgColor
            shape.lineJoin = .round
            shape.lineCap = .round
            layer.addSublayer(shape)
        }
        updateColors()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }
    private var scale: CGFloat {
        max(min(bounds.height, bounds.width) / CGFloat(2 * bridge.radius + 24), 0.01)
    }
    private var centre: CGPoint {
        let inset = (CGFloat(bridge.radius) + 12) * scale
        return CGPoint(x: leftHanded ? inset : bounds.width - inset, y: bounds.midY)
    }
    private var mirror: CGFloat { leftHanded ? -1 : 1 }
    private func screen(_ x: Double, _ y: Double) -> CGPoint {
        let m = mirror
        let sx = centre.x + m * CGFloat(x - bridge.ph[0]) * scale
        let sy = centre.y - CGFloat(y - bridge.ph[1]) * scale
        return CGPoint(x: sx, y: sy)
    }
    private func core(_ point: CGPoint) -> CGPoint {
        let m = mirror
        let x = bridge.ph[0] + Double(m * (point.x - centre.x) / scale)
        let y = bridge.ph[1] + Double((centre.y - point.y) / scale)
        return CGPoint(x: x, y: y)
    }
    private func petalPath(_ petal: Int) -> UIBezierPath {
        let path = UIBezierPath()
        path.move(to: screen(bridge.ph[0], bridge.ph[1]))
        for step in 0...12 {
            let angle = (Double(petal) * 45 - 22.5 + Double(step) * 45 / 12) * Double.pi / 180
            path.addLine(to: screen(bridge.ph[0] + bridge.radius * cos(angle), bridge.ph[1] + bridge.radius * sin(angle)))
        }
        path.close()
        return path
    }
    private func updateColors() {
        trail.strokeColor = UIColor.label.resolvedColor(with: traitCollection).cgColor
        trail.lineWidth = 3
        highlight.strokeColor = UIColor.label.resolvedColor(with: traitCollection).cgColor
        highlight.lineWidth = 4
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        updateColors()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        trail.frame = bounds
        highlight.frame = bounds
    }
    override func draw(_ rect: CGRect) {
        guard bridge.isReady, cells.count == 8 else { return }
        for petal in 0..<8 {
            let path = petalPath(petal)
            UIColor.systemBackground.setFill()
            UIColor.label.setStroke()
            path.lineWidth = 1
            path.fill(); path.stroke()
            for index in 0..<4 {
                let value = cells[petal][index]
                if value.isEmpty { continue }
                let label = shifted ? value.uppercased() : value
                let font = UIFont.systemFont(ofSize: max(11, CGFloat(index == 0 ? 19 : 12) * scale), weight: index == 0 ? .bold : .regular)
                let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.label]
                let size = (label as NSString).size(withAttributes: attributes)
                let position = bridge.spots[petal][index]
                let point = screen(position[0], position[1])
                (label as NSString).draw(at: CGPoint(x: point.x - size.width / 2, y: point.y - size.height / 2), withAttributes: attributes)
            }
        }
        drawSpaceCue()
    }
    private func drawSpaceCue() {
        let radiusPx = CGFloat(bridge.radius) * scale
        let edge = centre.x + (leftHanded ? radiusPx : -radiusPx)
        let area = leftHanded
            ? CGRect(x: edge, y: 0, width: max(0, bounds.width - edge), height: bounds.height)
            : CGRect(x: 0, y: 0, width: max(0, edge), height: bounds.height)
        guard area.width > 1, area.height > 1 else { return }
        let text = "пробел" as NSString
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 15),
            .foregroundColor: UIColor.secondaryLabel
        ]
        let size = text.size(withAttributes: attributes)
        text.draw(at: CGPoint(x: area.midX - size.width / 2, y: area.midY - size.height / 2), withAttributes: attributes)
    }
    private func append(_ touch: UITouch) {
        let location = touch.location(in: self)
        let p = core(location)
        points.append(contentsOf: [Double(p.x), Double(p.y)])
        if hypot(p.x - stillPoint.x, p.y - stillPoint.y) > 6 {
            stillPoint = p
            stillSince = touch.timestamp
        }
        if secure {
            trail.path = nil
            highlight.path = nil
            return
        }
        trailPath.addLine(to: location)
        trail.path = trailPath.cgPath
        let dx = Double(p.x) - bridge.ph[0], dy = Double(p.y) - bridge.ph[1]
        if hypot(dx, dy) >= 15 {
            let petal = (Int((atan2(dy, dx) / (Double.pi / 4)).rounded()) + 8) % 8
            highlight.path = petalPath(petal).cgPath
        } else { highlight.path = nil }
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard bridge.isReady, activeTouch == nil, let touch = touches.first else { return }
        let p = core(touch.location(in: self))
        activeTouch = touch
        points = []
        stillPoint = p
        stillSince = touch.timestamp
        trailPath = UIBezierPath()
        trailPath.move(to: touch.location(in: self))
        append(touch)
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        append(touch)
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        let end = core(touch.location(in: self))
        let originX = points.count >= 2 ? points[0] : Double(end.x)
        let originY = points.count >= 2 ? points[1] : Double(end.y)
        let length = hypot(Double(end.x) - originX, Double(end.y) - originY)
        let held = length >= pMin && (touch.timestamp - stillSince) * 1000 >= bridge.holdMilliseconds
        append(touch)
        onStroke?(points, held)
        clearTouch()
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        clearTouch()
    }
    func clearTouch() {
        activeTouch = nil
        points = []
        trail.path = nil
        highlight.path = nil
    }
}
