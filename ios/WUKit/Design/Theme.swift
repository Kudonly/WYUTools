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
    /// 工具图标方块圆角比例。安卓里是 0.2237 * 0.8，更多页单独放大 25%，
    /// 两者相乘正好回到 0.2237 —— 和 iOS 主屏图标同一套连续圆角。
    static let iconCornerRatio: CGFloat = 0.2237
    static let moreIconLabelSpacing: CGFloat = 5
    static let moreRowSpacing: CGFloat = 17
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

extension UInt32 {
    /// 往白色方向抬 [amount]（0...1），算出图标底色的高光端。
    func lightened(_ amount: Double) -> UInt32 {
        var result: UInt32 = 0
        for shift in stride(from: 16, through: 0, by: -8) {
            let channel = Double((self >> UInt32(shift)) & 0xFF)
            let mixed = (channel + (255 - channel) * amount).rounded()
            result |= UInt32(mixed) << UInt32(shift)
        }
        return result
    }
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
