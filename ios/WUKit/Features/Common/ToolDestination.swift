import SwiftUI

/// 工具入口 → 具体页面。已完成的走真实页面，其余走统一的待接入页。
struct ToolDestination: View {
    let tool: WKTool

    var body: some View {
        switch tool {
        case .today:
            TodayView()
        case .schedule:
            ScheduleView()
        case .lxt, .elec, .gym, .exam, .grades, .eval, .physical:
            PlaceholderView(tool: tool)
        }
    }
}

/// P1 才接真实数据的工具。宁可在页面上写清楚，也不放假数据。
struct PlaceholderView: View {
    let tool: WKTool

    @Environment(AppSettings.self) private var settings

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ToolBadge(tool: tool, size: 72)
                    .padding(.top, 28)

                Text(tool.title)
                    .font(.system(size: 24, weight: .bold))

                Text(tool.summary)
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)

                GlassCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("接口待接入")
                            .font(.system(size: 16, weight: .semibold))
                        Text("网络层已经备好，P1 阶段把该工具的请求与解析补上，页面就能填满真实数据。")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(.top, 6)

                NavigationLink {
                    NetworkCheckView()
                } label: {
                    GlassCard {
                        WKSettingRow(
                            title: "先测一下接口",
                            summary: "确认当前网络能访问 \(tool.title) 对应的后端域",
                            showsChevron: true
                        )
                    }
                }
                .buttonStyle(WKPressStyle())

                Spacer(minLength: 24)
            }
            .padding(.horizontal, WK.pagePadding)
        }
        .background(WKWallpaperBackground())
        .scrollIndicators(.hidden)
        .navigationTitle(tool.title)
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
    }
}

#Preview {
    NavigationStack {
        PlaceholderView(tool: .grades)
    }
    .environment(AppSettings.shared)
}
