import SwiftUI

/// 三个主页。底栏交给系统 TabView（iOS 26 起是原生液态玻璃），
/// 这里只负责标题与图标，顺序和 Android 的 常用 / 更多 / 我 一致。
enum AppTab: String, CaseIterable, Identifiable {
    case home
    case more
    case me

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "常用"
        case .more: return "更多"
        case .me: return "我"
        }
    }

    var symbol: String {
        switch self {
        case .home: return "wrench.and.screwdriver"
        case .more: return "square.grid.2x2"
        case .me: return "person"
        }
    }
}
