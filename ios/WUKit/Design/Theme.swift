import SwiftUI

/// 全局尺寸与配色。数值对齐 Android 端，保证两端观感一致。
enum WK {
    static let cardRadius: CGFloat = 28
    static let cardPadding: CGFloat = 18
    static let pagePadding: CGFloat = 16
    static let sectionSpacing: CGFloat = 12
    static let minTapHeight: CGFloat = 44
    static let bottomBarInset: CGFloat = 96
}

enum WKPalette {
    static let accent = Color(red: 0.24, green: 0.47, blue: 0.96)
    static let today = Color(red: 0.31, green: 0.60, blue: 0.98)
    static let schedule = Color(red: 0.42, green: 0.38, blue: 0.92)
    static let lxt = Color(red: 0.20, green: 0.62, blue: 0.72)
    static let elec = Color(red: 0.95, green: 0.62, blue: 0.20)
    static let gym = Color(red: 0.24, green: 0.68, blue: 0.44)
    static let exam = Color(red: 0.86, green: 0.35, blue: 0.35)
    static let grades = Color(red: 0.36, green: 0.42, blue: 0.78)
    static let eval = Color(red: 0.72, green: 0.40, blue: 0.72)
    static let physical = Color(red: 0.30, green: 0.55, blue: 0.40)
}

/// 壁纸位：先给一层柔和渐变，后续可接相册图片与玻璃采样。
struct PageBackground: View {
    var body: some View {
        LinearGradient(
            colors: [
                Color(red: 0.93, green: 0.95, blue: 1.00),
                Color(red: 0.97, green: 0.96, blue: 0.99),
                Color(red: 0.95, green: 0.98, blue: 0.98)
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
}
