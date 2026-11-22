import SwiftUI

extension View {
    /// iOS 26 起系统底栏可以随滚动收起，给内容让位；老系统忽略。
    @ViewBuilder
    func wkTabBarMinimizeOnScroll() -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
        #else
        self
        #endif
    }

    /// 详情页整页铺开，底栏让位——和 Android 的整页打开一致。
    func wkFullScreenDetail() -> some View {
        toolbar(.hidden, for: .tabBar)
    }
}

/// 顶栏图标按钮。工具栏本身的玻璃交给系统，这里只管语义和点击区。
struct WKToolbarIcon: View {
    let symbol: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .semibold))
                .frame(width: 28, height: 28)
        }
        .accessibilityLabel(label)
    }
}

/// 大按钮：强调色胶囊，用于登录、保存这类主操作。
struct WKProminentButton: View {
    let title: String
    var symbol: String? = nil
    var enabled: Bool = true
    let action: () -> Void

    @Environment(AppSettings.self) private var settings

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let symbol {
                    Image(systemName: symbol)
                        .font(.system(size: 15, weight: .semibold))
                }
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(settings.accentColor.gradient, in: Capsule())
        }
        .buttonStyle(WKPressStyle())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
    }
}
