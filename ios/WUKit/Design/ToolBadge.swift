import SwiftUI

/// 工具图标块：圆角方块 + 渐变底。
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
                in: RoundedRectangle(cornerRadius: size * 0.30, style: .continuous)
            )
    }
}

/// 课程色块用同一套调色板，最多 8 种。
enum CoursePalette {
    static let colors: [Color] = [
        Color(red: 0.31, green: 0.47, blue: 0.90),
        Color(red: 0.20, green: 0.62, blue: 0.56),
        Color(red: 0.88, green: 0.52, blue: 0.24),
        Color(red: 0.72, green: 0.38, blue: 0.62),
        Color(red: 0.36, green: 0.58, blue: 0.30),
        Color(red: 0.85, green: 0.36, blue: 0.38),
        Color(red: 0.28, green: 0.52, blue: 0.70),
        Color(red: 0.55, green: 0.44, blue: 0.72)
    ]

    static func color(_ index: Int) -> Color {
        colors[abs(index) % colors.count]
    }
}
