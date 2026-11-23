import SwiftUI

/// 工具图标用 Android 端同一份 Material Icons（Outlined）矢量路径。
/// 路径数据直接从 material-icons-extended 的源码里导出来（24×24 viewBox），
/// 所以字形、笔画粗细、内孔位置都和安卓逐像素一致。
enum WKGlyph: String, CaseIterable {
    case today
    case calendarMonth
    case waterDrop
    case bolt
    case basketball
    case assignment
    case school
    case rateReview
    case fitnessCenter

    /// 仅含 M/L/H/V/C/S/Q/T/Z（含相对指令），没有弧线。
    var svgPath: String {
        switch self {
        case .today:
            return "M19 3 h-1 L18 1 h-2 v2 L8 3 L8 1 L6 1 v2 L5 3 c-1.11 0 -2 .9 -2 2 v14 c0 1.1 .89 2 2 2 h14 c1.1 0 2 -.9 2 -2 L21 5 c0 -1.1 -.9 -2 -2 -2 Z M19 19 L5 19 L5 9 h14 v10 Z M19 7 L5 7 L5 5 h14 v2 Z M7 11 h5 v5 L7 16 Z"
        case .calendarMonth:
            return "M19 4 h-1 V2 h-2 v2 H8 V2 H6 v2 H5 C3.89 4 3.01 4.9 3.01 6 L3 20 c0 1.1 .89 2 2 2 h14 c1.1 0 2 -.9 2 -2 V6 C21 4.9 20.1 4 19 4 Z M19 20 H5 V10 h14 V20 Z M19 8 H5 V6 h14 V8 Z M9 14 H7 v-2 h2 V14 Z M13 14 h-2 v-2 h2 V14 Z M17 14 h-2 v-2 h2 V14 Z M9 18 H7 v-2 h2 V18 Z M13 18 h-2 v-2 h2 V18 Z M17 18 h-2 v-2 h2 V18 Z"
        case .waterDrop:
            return "M12 2 c-5.33 4.55 -8 8.48 -8 11.8 c0 4.98 3.8 8.2 8 8.2 s8 -3.22 8 -8.2 C20 10.48 17.33 6.55 12 2 Z M12 20 c-3.35 0 -6 -2.57 -6 -6.2 c0 -2.34 1.95 -5.44 6 -9.14 c4.05 3.7 6 6.79 6 9.14 C18 17.43 15.35 20 12 20 Z M7.83 14 c.37 0 .67 .26 .74 .62 c.41 2.22 2.28 2.98 3.64 2.87 c.43 -.02 .79 .32 .79 .75 c0 .4 -.32 .73 -.72 .75 c-2.13 .13 -4.62 -1.09 -5.19 -4.12 C7.01 14.42 7.37 14 7.83 14 Z"
        case .bolt:
            return "M11 21 h-1 l1 -7 H7.5 c-.88 0 -.33 -.75 -.31 -.78 C8.48 10.94 10.42 7.54 13.01 3 h1 l-1 7 h3.51 c.4 0 .62 .19 .4 .66 C12.97 17.55 11 21 11 21 Z"
        case .basketball:
            return "M12 2 C6.48 2 2 6.48 2 12 c0 5.52 4.48 10 10 10 s10 -4.48 10 -10 C22 6.48 17.52 2 12 2 Z M5.23 7.75 C6.1 8.62 6.7 9.74 6.91 11 H4.07 C4.22 9.82 4.63 8.72 5.23 7.75 Z M4.07 13 h2.84 c-.21 1.26 -.81 2.38 -1.68 3.25 C4.63 15.28 4.22 14.18 4.07 13 Z M11 19.93 c-1.73 -.22 -3.29 -1 -4.49 -2.14 c1.3 -1.24 2.19 -2.91 2.42 -4.79 H11 V19.93 Z M11 11 H8.93 C8.69 9.12 7.81 7.44 6.5 6.2 C7.71 5.06 9.27 4.29 11 4.07 V11 Z M19.93 11 h-2.84 c.21 -1.26 .81 -2.38 1.68 -3.25 C19.37 8.72 19.78 9.82 19.93 11 Z M13 4.07 c1.73 .22 3.29 .99 4.5 2.13 c-1.31 1.24 -2.19 2.92 -2.43 4.8 H13 V4.07 Z M13 19.93 V13 h2.07 c.24 1.88 1.12 3.55 2.42 4.79 C16.29 18.93 14.73 19.71 13 19.93 Z M18.77 16.25 c-.87 -.86 -1.46 -1.99 -1.68 -3.25 h2.84 C19.78 14.18 19.37 15.28 18.77 16.25 Z"
        case .assignment:
            return "M7 15 h7 v2 L7 17 Z M7 11 h10 v2 L7 13 Z M7 7 h10 v2 L7 9 Z M19 3 h-4.18 C14.4 1.84 13.3 1 12 1 c-1.3 0 -2.4 .84 -2.82 2 L5 3 c-.14 0 -.27 .01 -.4 .04 c-.39 .08 -.74 .28 -1.01 .55 c-.18 .18 -.33 .4 -.43 .64 c-.1 .23 -.16 .49 -.16 .77 v14 c0 .27 .06 .54 .16 .78 s.25 .45 .43 .64 c.27 .27 .62 .47 1.01 .55 c.13 .02 .26 .03 .4 .03 h14 c1.1 0 2 -.9 2 -2 L21 5 c0 -1.1 -.9 -2 -2 -2 Z M12 2.75 c.41 0 .75 .34 .75 .75 s-.34 .75 -.75 .75 s-.75 -.34 -.75 -.75 s.34 -.75 .75 -.75 Z M19 19 L5 19 L5 5 h14 v14 Z"
        case .school:
            return "M12 3 L1 9 l4 2.18 v6 L12 21 l7 -3.82 v-6 l2 -1.09 L21 17 h2 L23 9 L12 3 Z M18.82 9 L12 12.72 L5.18 9 L12 5.28 L18.82 9 Z M17 15.99 l-5 2.73 l-5 -2.73 v-3.72 L12 15 l5 -2.73 v3.72 Z"
        case .rateReview:
            return "M20 2 L4 2 c-1.1 0 -1.99 .9 -1.99 2 L2 22 l4 -4 h14 c1.1 0 2 -.9 2 -2 L22 4 c0 -1.1 -.9 -2 -2 -2 Z M20 16 L5.17 16 l-.59 .59 l-.58 .58 L4 4 h16 v12 Z M10.5 14 L18 14 v-2 h-5.5 Z M14.36 8.13 c.2 -.2 .2 -.51 0 -.71 l-1.77 -1.77 c-.2 -.2 -.51 -.2 -.71 0 L6 11.53 L6 14 h2.47 l5.89 -5.87 Z"
        case .fitnessCenter:
            return "M20.57 14.86 L22 13.43 L20.57 12 L17 15.57 L8.43 7 L12 3.43 L10.57 2 L9.14 3.43 L7.71 2 L5.57 4.14 L4.14 2.71 L2.71 4.14 l1.43 1.43 L2 7.71 l1.43 1.43 L2 10.57 L3.43 12 L7 8.43 L15.57 17 L12 20.57 L13.43 22 l1.43 -1.43 L16.29 22 l2.14 -2.14 l1.43 1.43 l1.43 -1.43 l-1.43 -1.43 L22 16.29 l-1.43 -1.43 Z"
        }
    }
}

/// 把 SVG path 数据画成 SwiftUI Path。覆盖 M/L/H/V/C/S/Q/T/Z 及其相对写法，
/// 也支持省略重复指令字母的连续参数（矢量图标里很常见）。
struct SVGPathShape: Shape {
    let data: String

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let tokens = SVGPathToken.tokenize(data)
        let side = min(rect.width, rect.height)
        let scale = side / 24
        let origin = CGPoint(
            x: rect.minX + (rect.width - side) / 2,
            y: rect.minY + (rect.height - side) / 2
        )

        func absolute(_ x: Double, _ y: Double) -> CGPoint {
            CGPoint(x: origin.x + CGFloat(x) * scale, y: origin.y + CGFloat(y) * scale)
        }

        var current = CGPoint.zero
        var start = CGPoint.zero
        var lastCubic: CGPoint?
        var lastQuad: CGPoint?
        var command: Character = "M"
        var index = 0

        func offset(_ x: Double, _ y: Double) -> CGPoint {
            CGPoint(x: current.x + CGFloat(x) * scale, y: current.y + CGFloat(y) * scale)
        }

        func number() -> Double? {
            guard index < tokens.count, case .number(let value) = tokens[index] else { return nil }
            index += 1
            return value
        }

        while index < tokens.count {
            if case .command(let next) = tokens[index] {
                index += 1
                if next == "Z" || next == "z" {
                    path.closeSubpath()
                    current = start
                    lastCubic = nil
                    lastQuad = nil
                    command = "M"
                } else {
                    // 一条指令可以带一串参数：一直按它处理，直到读到下一个字母。
                    command = next
                }
                continue
            }

            let relative = command.isLowercase

            switch Character(command.uppercased()) {
            case "M":
                guard let x = number(), let y = number() else { break }
                let target = relative ? offset(x, y) : absolute(x, y)
                path.move(to: target)
                current = target
                start = target
                // 一组坐标之后，M/m 的后续参数按 L/l 处理。
                command = relative ? "l" : "L"
                lastCubic = nil
                lastQuad = nil

            case "L":
                guard let x = number(), let y = number() else { break }
                let target = relative ? offset(x, y) : absolute(x, y)
                path.addLine(to: target)
                current = target
                lastCubic = nil
                lastQuad = nil

            case "H":
                guard let x = number() else { break }
                let target = CGPoint(x: relative ? current.x + CGFloat(x) * scale : origin.x + CGFloat(x) * scale, y: current.y)
                path.addLine(to: target)
                current = target
                lastCubic = nil
                lastQuad = nil

            case "V":
                guard let y = number() else { break }
                let target = CGPoint(x: current.x, y: relative ? current.y + CGFloat(y) * scale : origin.y + CGFloat(y) * scale)
                path.addLine(to: target)
                current = target
                lastCubic = nil
                lastQuad = nil

            case "C":
                guard let x1 = number(), let y1 = number(),
                      let x2 = number(), let y2 = number(),
                      let x = number(), let y = number() else { break }
                let c1 = relative ? offset(x1, y1) : absolute(x1, y1)
                let c2 = relative ? offset(x2, y2) : absolute(x2, y2)
                let end = relative ? offset(x, y) : absolute(x, y)
                path.addCurve(to: end, control1: c1, control2: c2)
                current = end
                lastCubic = c2
                lastQuad = nil

            case "S":
                guard let x2 = number(), let y2 = number(),
                      let x = number(), let y = number() else { break }
                let c1 = lastCubic.map { CGPoint(x: 2 * current.x - C:\Users\26680\.workbuddy\binaries\PortableGit\versions\1.3.0\bin\bash.exe.x, y: 2 * current.y - C:\Users\26680\.workbuddy\binaries\PortableGit\versions\1.3.0\bin\bash.exe.y) } ?? current
                let c2 = relative ? offset(x2, y2) : absolute(x2, y2)
                let end = relative ? offset(x, y) : absolute(x, y)
                path.addCurve(to: end, control1: c1, control2: c2)
                current = end
                lastCubic = c2
                lastQuad = nil

            case "Q":
                guard let x1 = number(), let y1 = number(),
                      let x = number(), let y = number() else { break }
                let control = relative ? offset(x1, y1) : absolute(x1, y1)
                let end = relative ? offset(x, y) : absolute(x, y)
                path.addQuadCurve(to: end, control: control)
                current = end
                lastQuad = control
                lastCubic = nil

            case "T":
                guard let x = number(), let y = number() else { break }
                let control = lastQuad.map { CGPoint(x: 2 * current.x - C:\Users\26680\.workbuddy\binaries\PortableGit\versions\1.3.0\bin\bash.exe.x, y: 2 * current.y - C:\Users\26680\.workbuddy\binaries\PortableGit\versions\1.3.0\bin\bash.exe.y) } ?? current
                let end = relative ? offset(x, y) : absolute(x, y)
                path.addQuadCurve(to: end, control: control)
                current = end
                lastQuad = control
                lastCubic = nil

            default:
                // 未知指令：吃掉一个数字，避免死循环。
                _ = number()
            }
        }

        return path
    }
}

/// 极小化的 path 词法分析：指令字母 + 数字（含负号、小数、指数）。
enum SVGPathToken {
    case command(Character)
    case number(Double)

    static func tokenize(_ text: String) -> [SVGPathToken] {
        var tokens: [SVGPathToken] = []
        var index = text.startIndex

        while index < text.endIndex {
            let character = text[index]
            if character.isLetter {
                tokens.append(.command(character))
                index = text.index(after: index)
                continue
            }
            if character == "," || character == " " || character.isNewline || character == "\t" {
                index = text.index(after: index)
                continue
            }

            var literal = String(character)
            var seenDot = character == "."
            index = text.index(after: index)
            while index < text.endIndex {
                let next = text[index]
                if next.isNumber {
                    literal.append(next)
                    index = text.index(after: index)
                } else if next == "." && !seenDot {
                    seenDot = true
                    literal.append(next)
                    index = text.index(after: index)
                } else {
                    break
                }
            }
            if let value = Double(literal) {
                tokens.append(.number(value))
            }
        }
        return tokens
    }
}

/// 按 24 单位等比缩放地画一个 Material 图标。
struct MaterialGlyph: View {
    let glyph: WKGlyph
    var size: CGFloat
    var color: Color = .white

    var body: some View {
        SVGPathShape(data: glyph.svgPath)
            .fill(color, style: FillStyle(eoFill: false))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
