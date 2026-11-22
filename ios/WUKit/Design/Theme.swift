import SwiftUI

/// 全局尺寸。数值对齐 Android 端（16dp 页边距、12dp 卡片间距、28dp 圆角）。
enum WK {
    static let cardRadius: CGFloat = 28
    static let cardPadding: CGFloat = 18
    static let pagePadding: CGFloat = 16
    static let sectionSpacing: CGFloat = 12
    static let sectionTitlePadding: CGFloat = 20
    static let minTapHeight: CGFloat = 44

    /// 更多页：4 列图标网格，尺寸与行距照搬 Android。
    static let moreColumns = 4
    static let morePagePadding: CGFloat = 22
    static let moreIconSize: CGFloat = 66
    static let moreIconLabelSpacing: CGFloat = 6
    static let moreRowSpacing: CGFloat = 22
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

/// 工具色板。强调色跟着设置走，工具色保持固定。
enum WKPalette {
    static let today = Color(hex: 0x3F7BF6)
    static let schedule = Color(hex: 0x6B5FE0)
    static let lxt = Color(hex: 0x2E9CA8)
    static let elec = Color(hex: 0xE29A2D)
    static let gym = Color(hex: 0x34985C)
    static let exam = Color(hex: 0xD9574F)
    static let grades = Color(hex: 0x5B6BD0)
    static let eval = Color(hex: 0xB461B0)
    static let physical = Color(hex: 0x4C8C63)
}

/// 壁纸层：渐变或自定义照片，铺满整屏，所有页面共用。
struct WKWallpaperBackground: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                if settings.wallpaper == .custom, let image = settings.customWallpaper {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: proxy.size.width, height: proxy.size.height)
                        .clipped()
                        .overlay(Color.black.opacity(dim))
                } else {
                    LinearGradient(
                        colors: settings.wallpaper.stops(dark: scheme == .dark) ?? [],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                }
            }
        }
        .ignoresSafeArea()
    }

    private var dim: Double {
        (1 - settings.wallpaperBrightness).clamped(to: 0...0.5)
    }
}

extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

/// 页面内容统一用的前景/次要色，避免深色浅色各写一套。
enum WKInk {
    static let primary = Color.primary
    static let secondary = Color.secondary
    static let tertiary = Color.primary.opacity(0.35)
}
