import SwiftUI

/// 常用卡片管理：上面是常用，下面是更多，两边都能排序，
/// 右侧的加号/减号负责在两边之间搬。
struct CommonCardsView: View {
    @Bindable private var settings = AppSettings.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if settings.commonTools.isEmpty {
                        Text("常用还是空的，从下面挑几张上来吧。")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(settings.commonTools) { tool in
                            row(tool, inCommon: true)
                        }
                        .onMove { settings.moveCommon(from: $0, to: $1) }
                    }
                } header: {
                    Text("常用卡片")
                }

                Section {
                    if settings.moreTools.isEmpty {
                        Text("所有工具都在常用页了。")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(settings.moreTools) { tool in
                            row(tool, inCommon: false)
                        }
                        .onMove { settings.moveMore(from: $0, to: $1) }
                    }
                } header: {
                    Text("更多工具")
                } footer: {
                    Text("右上角「编辑」可以对调顺序；右侧按钮把卡片搬到另一边。")
                }
            }
            .navigationTitle("常用卡片")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    EditButton()
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }

    private func row(_ tool: WKTool, inCommon: Bool) -> some View {
        HStack(spacing: 12) {
            ToolBadge(tool: tool, size: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(tool.title)
                    .font(.system(size: 15, weight: .medium))
                Text(tool.summary)
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            Button {
                withAnimation(.snappy(duration: 0.2)) {
                    settings.setInCommon(tool, !inCommon)
                }
            } label: {
                Image(systemName: inCommon ? "minus.circle" : "plus.circle")
                    .font(.system(size: 20))
                    .foregroundStyle(inCommon ? Color.secondary : settings.accentColor)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(inCommon ? "从常用移除" : "加到常用")
        }
        .padding(.vertical, 2)
    }
}

#Preview {
    CommonCardsView()
        .environment(AppSettings.shared)
}
