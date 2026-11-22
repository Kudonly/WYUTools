import SwiftUI

/// 原生壳：系统 TabView（iOS 26 起底栏就是液态玻璃）+ 全屏壁纸。
struct RootView: View {
    @State private var settings = AppSettings.shared
    @State private var tab: AppTab = .home

    var body: some View {
        ZStack {
            WKWallpaperBackground()

            TabView(selection: $tab) {
                Tab(AppTab.home.title, systemImage: AppTab.home.symbol, value: AppTab.home) {
                    NavigationStack {
                        HomeView()
                    }
                }

                Tab(AppTab.more.title, systemImage: AppTab.more.symbol, value: AppTab.more) {
                    NavigationStack {
                        MoreView()
                    }
                }

                Tab(AppTab.me.title, systemImage: AppTab.me.symbol, value: AppTab.me) {
                    NavigationStack {
                        MeView()
                    }
                }
            }
            .tint(settings.accentColor)
            .wkTabBarMinimizeOnScroll()
        }
        .environment(settings)
        .preferredColorScheme(settings.themeMode.colorScheme)
    }
}

#Preview {
    RootView()
}
