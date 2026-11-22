import SwiftUI

enum AppTab: String, CaseIterable, Identifiable {
    case home = "常用"
    case more = "更多"
    case me = "我"

    var id: String { rawValue }

    var symbol: String {
        switch self {
        case .home: return "square.grid.2x2"
        case .more: return "square.grid.3x3"
        case .me: return "person"
        }
    }
}

/// 悬浮胶囊底栏：选中项展开成带文字的胶囊。
struct BottomBar: View {
    @Binding var selection: AppTab

    var body: some View {
        HStack(spacing: 4) {
            ForEach(AppTab.allCases) { tab in
                let active = tab == selection
                Button {
                    withAnimation(.snappy(duration: 0.24)) {
                        selection = tab
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: tab.symbol)
                            .font(.system(size: 15, weight: .medium))
                        if active {
                            Text(tab.rawValue)
                                .font(.system(size: 13, weight: .medium))
                                .fixedSize()
                        }
                    }
                    .foregroundStyle(active ? Color.white : Color.secondary)
                    .padding(.horizontal, active ? 16 : 12)
                    .frame(height: 40)
                    .background {
                        if active {
                            Capsule().fill(WKPalette.accent)
                        }
                    }
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay {
            Capsule().strokeBorder(Color.white.opacity(0.24), lineWidth: 0.5)
        }
        .shadow(color: Color.black.opacity(0.10), radius: 16, x: 0, y: 8)
        .padding(.bottom, 10)
    }
}
