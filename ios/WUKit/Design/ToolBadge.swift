import SwiftUI

/// 工具图标块：连续圆角方块 + 渐变底，比例向 iOS 图标看齐。
struct ToolBadge: View {
    let tool: WKTool
    var size: CGFloat = 40

    var body: some View {
        Image(systemName: tool.symbol)
            .font(.system(size: size * 0.44, weight: .medium))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(
                tool.tint.gradient,
                in: RoundedRectangle(cornerRadius: size * 0.265, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: size * 0.265, style: .continuous)
                    .strokeBorder(
                        LinearGradient(
                            colors: [Color.white.opacity(0.45), Color.white.opacity(0.04)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 0.8
                    )
            }
            .shadow(color: tool.tint.opacity(0.28), radius: size * 0.12, x: 0, y: size * 0.06)
    }
}

/// 列表行里的小图标：不铺底色，直接吃强调色，和 Android 卡片行一致。
struct ToolGlyph: View {
    let tool: WKTool
    var size: CGFloat = 26

    @Environment(AppSettings.self) private var settings

    var body: some View {
        Image(systemName: tool.symbol)
            .font(.system(size: size * 0.92, weight: .regular))
            .symbolRenderingMode(.hierarchical)
            .foregroundStyle(settings.accentColor)
            .frame(width: size + 2, height: size + 2)
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
