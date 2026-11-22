import SwiftUI

/// 三页骨架：常用 / 更多 / 我，底部悬浮胶囊切换。
struct RootView: View {
    @State private var tab: AppTab = .home

    var body: some View {
        ZStack(alignment: .bottom) {
            PageBackground()

            Group {
                switch tab {
                case .home:
                    NavigationStack { HomeView() }
                case .more:
                    NavigationStack { MoreView() }
                case .me:
                    NavigationStack { MeView() }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            BottomBar(selection: $tab)
        }
        .tint(WKPalette.accent)
    }
}

#Preview {
    RootView()
}
