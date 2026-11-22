import SwiftUI

/// 更多页：像手机主屏一样的 4 列图标网格，编辑态可以长按拖动换位。
struct MoreView: View {
    @Environment(AppSettings.self) private var settings
    @State private var editing = false
    @State private var jiggle = false

    private let columns = Array(
        repeating: GridItem(.flexible(), spacing: 8),
        count: WK.moreColumns
    )

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                LazyVGrid(columns: columns, spacing: WK.moreRowSpacing) {
                    ForEach(settings.moreTools) { tool in
                        tile(tool)
                    }
                }

                if settings.moreTools.isEmpty {
                    Text("所有工具都在常用页了。")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                        .padding(.top, 40)
                }

                if editing {
                    Text("长按图标拖动，松手即完成换位。")
                        .font(.system(size: 12))
                        .foregroundStyle(.tertiary)
                        .padding(.top, 8)
                }
            }
            .padding(.horizontal, WK.morePagePadding)
            .padding(.top, 14)
            .padding(.bottom, 28)
        }
        .background(WKWallpaperBackground())
        .scrollIndicators(.hidden)
        .navigationTitle("更多")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(editing ? "完成" : "编辑") {
                    withAnimation(.snappy(duration: 0.2)) {
                        editing.toggle()
                        jiggle = editing
                    }
                }
                .font(.system(size: 16, weight: .semibold))
            }
        }
        .navigationDestination(for: WKTool.self) { tool in
            ToolDestination(tool: tool)
        }
        .sensoryFeedback(.selection, trigger: editing)
    }

    @ViewBuilder
    private func tile(_ tool: WKTool) -> some View {
        if editing {
            iconLabel(tool)
                .rotationEffect(.degrees(jiggle ? 1.4 : -1.4))
                .animation(
                    .easeInOut(duration: 0.15).repeatForever(autoreverses: true),
                    value: jiggle
                )
                .draggable(tool.rawValue) {
                    iconLabel(tool)
                        .scaleEffect(1.12)
                }
                .dropDestination(for: String.self) { items, _ in
                    guard let raw = items.first, let from = WKTool(rawValue: raw) else { return false }
                    withAnimation(.snappy(duration: 0.22)) {
                        settings.moveInMore(from, before: tool)
                    }
                    return true
                }
        } else {
            NavigationLink(value: tool) {
                iconLabel(tool)
            }
            .buttonStyle(WKPressStyle())
        }
    }

    private func iconLabel(_ tool: WKTool) -> some View {
        VStack(spacing: WK.moreIconLabelSpacing) {
            ToolBadge(tool: tool, size: WK.moreIconSize)
            Text(tool.title)
                .font(.system(size: 12.5))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
    }
}

#Preview {
    NavigationStack {
        MoreView()
    }
    .environment(AppSettings.shared)
}
