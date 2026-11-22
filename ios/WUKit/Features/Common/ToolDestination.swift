import SwiftUI

/// 工具入口 → 具体页面。
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

/// P1 才接真实数据的工具，先用统一的占位页，保证导航结构完整。
struct PlaceholderView: View {
    let tool: WKTool

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                ToolBadge(tool: tool, size: 64)
                    .padding(.top, 40)

                Text(tool.title)
                    .font(.system(size: 24, weight: .bold))

                Text(tool.summary)
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)

                GlassCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("待接入")
                            .font(.system(size: 15, weight: .semibold))
                        Text("网络层已在 Core/Network 备好，P1 阶段把该工具的接口与解析补上即可。")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.top, 8)

                Spacer(minLength: 40)
            }
            .padding(.horizontal, WK.pagePadding)
        }
        .background(PageBackground())
        .navigationTitle(tool.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}
