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

/// 课程色块用同一套调色板，最多 8 种。
enum CoursePalette {
    static let colors: [Color] = [
        Color(hex: 0x4F78E6),
        Color(hex: 0x339F90),
        Color(hex: 0xE08539),
        Color(hex: 0xB8619E),
        Color(hex: 0x5C9448),
        Color(hex: 0xD95C5C),
        Color(hex: 0x4785B3),
        Color(hex: 0x8C70B8)
    ]

    static func color(_ index: Int) -> Color {
        colors[abs(index) % colors.count]
    }
}
