import SwiftUI
import UIKit

/// 更多页：4 列图标网格，编辑态长按拖动时图标贴着手指走，
/// 跨过相邻格子就实时换位（跟系统主屏的手感一致）。
struct MoreView: View {
    @Environment(AppSettings.self) private var settings
    @State private var editing = false

    // 拖动状态
    @State private var dragging: WKTool?
    @State private var dragTranslation: CGSize = .zero
    @State private var baseShift: CGSize = .zero

    private let columnSpacing: CGFloat = 8
    private let rowSpacing = WK.moreRowSpacing
    private let tileHeight: CGFloat = 92

    var body: some View {
        GeometryReader { proxy in
            let available = proxy.size.width - WK.morePagePadding * 2
            let cellWidth = max(56, (available - columnSpacing * 3) / 4)

            ScrollView {
                VStack(spacing: 16) {
                    LazyVGrid(
                        columns: Array(
                            repeating: GridItem(.fixed(cellWidth), spacing: columnSpacing),
                            count: 4
                        ),
                        spacing: rowSpacing
                    ) {
                        ForEach(settings.moreTools) { tool in
                            tile(tool, cellWidth: cellWidth)
                        }
                    }

                    if settings.moreTools.isEmpty {
                        Text("所有工具都在常用页了。")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                            .padding(.top, 40)
                    }

                    if editing {
                        Text("按住图标拖动即可换位，点右上角「完成」退出。")
                            .font(.system(size: 12))
                            .foregroundStyle(.tertiary)
                            .padding(.top, 8)
                    }
                }
                .padding(.horizontal, WK.morePagePadding)
                .padding(.top, 10)
                .padding(.bottom, 28)
                .coordinateSpace(name: "moreGrid")
            }
            .scrollDisabled(dragging != nil)
            .background(WKWallpaperBackground())
            .scrollIndicators(.hidden)
        }
        .navigationTitle("更多")
        .toolbarTitleDisplayMode(.inlineLarge)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(editing ? "完成" : "编辑") {
                    withAnimation(.snappy(duration: 0.22)) {
                        editing.toggle()
                        endDrag(animated: true)
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

    // MARK: - 图标

    @ViewBuilder
    private func tile(_ tool: WKTool, cellWidth: CGFloat) -> some View {
        if editing {
            iconLabel(tool)
                .frame(height: tileHeight)
                .scaleEffect(dragging == tool ? 1.08 : 1)
                .offset(dragging == tool ? dragTranslation : .zero)
                .zIndex(dragging == tool ? 10 : 0)
                .shadow(
                    color: .black.opacity(dragging == tool ? 0.22 : 0),
                    radius: dragging == tool ? 14 : 0,
                    y: dragging == tool ? 8 : 0
                )
                .animation(dragging == tool ? nil : .snappy(duration: 0.2), value: settings.moreTools)
                .gesture(dragGesture(tool, cellWidth: cellWidth))
        } else {
            NavigationLink(value: tool) {
                iconLabel(tool)
                    .frame(height: tileHeight)
            }
            .buttonStyle(WKPressStyle())
            .onLongPressGesture(minimumDuration: 0.45) {
                WKHaptics.impact(.medium)
                withAnimation(.snappy(duration: 0.22)) { editing = true }
            }
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

    // MARK: - 拖动

    private func dragGesture(_ tool: WKTool, cellWidth: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 3, coordinateSpace: .named("moreGrid"))
            .onChanged { value in
                if dragging == nil {
                    dragging = tool
                    dragTranslation = .zero
                    baseShift = .zero
                    WKHaptics.impact(.light)
                }
                guard dragging == tool else { return }

                reorderIfNeeded(tool, at: value.location, cellWidth: cellWidth)
                dragTranslation = CGSize(
                    width: value.translation.width - baseShift.width,
                    height: value.translation.height - baseShift.height
                )
            }
            .onEnded { _ in
                WKHaptics.impact(.rigid)
                endDrag(animated: true)
            }
    }

    private func reorderIfNeeded(_ tool: WKTool, at location: CGPoint, cellWidth: CGFloat) {
        guard let current = settings.moreTools.firstIndex(of: tool) else { return }
        let stepX = cellWidth + columnSpacing
        let stepY = tileHeight + rowSpacing
        let column = min(3, max(0, Int(location.x / stepX)))
        let row = max(0, Int(location.y / stepY))
        let target = min(row * 4 + column, settings.moreTools.count - 1)
        guard target != current else { return }

        let fromColumn = current % 4
        let fromRow = current / 4
        let toColumn = target % 4
        let toRow = target / 4
        baseShift.width += CGFloat(toColumn - fromColumn) * stepX
        baseShift.height += CGFloat(toRow - fromRow) * stepY

        withAnimation(.snappy(duration: 0.22)) {
            settings.moreTools.move(fromOffsets: IndexSet(integer: current), toOffset: target)
        }
        WKHaptics.selection()
    }

    private func endDrag(animated: Bool) {
        guard dragging != nil || dragTranslation != .zero else { return }
        if animated {
            withAnimation(.snappy(duration: 0.24)) {
                dragTranslation = .zero
            }
        } else {
            dragTranslation = .zero
        }
        dragging = nil
        baseShift = .zero
    }
}

/// 触感反馈的薄封装，省得到处 import UIKit。
enum WKHaptics {
    static func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.prepare()
        generator.impactOccurred()
    }

    static func selection() {
        let generator = UISelectionFeedbackGenerator()
        generator.prepare()
        generator.selectionChanged()
    }

    static func notify(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        let generator = UINotificationFeedbackGenerator()
        generator.prepare()
        generator.notificationOccurred(type)
    }
}

#Preview {
    NavigationStack {
        MoreView()
    }
    .environment(AppSettings.shared)
}
