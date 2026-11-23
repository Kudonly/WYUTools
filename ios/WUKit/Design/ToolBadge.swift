import SwiftUI

/// 工具图标方块，与 Android 更多页的图标 1:1：
/// 66pt 方块、圆角为边长的 22.37%、左上到右下的同色渐变、白色字形占一半宽。
struct ToolBadge: View {
    let tool: WKTool
    var size: CGFloat = 40

    private var radius: CGFloat { size * WK.iconCornerRatio }

    var body: some View {
        MaterialGlyph(glyph: tool.glyph, size: size * 0.5)
            .frame(width: size, height: size)
            .background(
                tool.iconGradient,
                in: RoundedRectangle(cornerRadius: radius, style: .continuous)
            )
            .accessibilityElement()
            .accessibilityLabel(tool.title)
    }
}

/// 卡片行里的裸图标：不铺底色，直接吃强调色，和 Android 的 ToolEntry 一致。
struct ToolGlyph: View {
    let tool: WKTool
    var size: CGFloat = 26

    @Environment(AppSettings.self) private var settings

    var body: some View {
        MaterialGlyph(glyph: tool.glyph, size: size, color: settings.accentColor)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// 课程色块：与安卓 `LightCourseColors` / `DarkCourseColors` 同一套底色与文字色。
enum CoursePalette {
    struct Pair {
        let background: Color
        let content: Color
    }

    private static let lightPairs: [Pair] = [
        Pair(background: Color(hex: 0xDCE9FF), content: Color(hex: 0x173A6B)),
        Pair(background: Color(hex: 0xD9F2DE), content: Color(hex: 0x14522A)),
        Pair(background: Color(hex: 0xFFE8C7), content: Color(hex: 0x7A3D00)),
        Pair(background: Color(hex: 0xEADFFF), content: Color(hex: 0x4A2578)),
        Pair(background: Color(hex: 0xD2F2EE), content: Color(hex: 0x0D4F47)),
        Pair(background: Color(hex: 0xFFDEE8), content: Color(hex: 0x7A1F3D))
    ]

    private static let darkPairs: [Pair] = [
        Pair(background: Color(hex: 0x233A5C), content: Color(hex: 0xD6E6FF)),
        Pair(background: Color(hex: 0x1E4431), content: Color(hex: 0xC7F0D3)),
        Pair(background: Color(hex: 0x4A3417), content: Color(hex: 0xFFDFA8)),
        Pair(background: Color(hex: 0x3A2A52), content: Color(hex: 0xEAD9FF)),
        Pair(background: Color(hex: 0x17453F), content: Color(hex: 0xC6F3E9)),
        Pair(background: Color(hex: 0x4B2333), content: Color(hex: 0xFFD6E2))
    ]

    static func pair(_ index: Int, dark: Bool) -> Pair {
        let list = dark ? darkPairs : lightPairs
        return list[abs(index) % list.count]
    }

    /// 今日页的小色条只用到底色。
    static func color(_ index: Int) -> Color {
        pair(index, dark: false).background
    }
}
