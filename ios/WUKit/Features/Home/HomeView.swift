import SwiftUI

/// 常用页：一张张玻璃卡竖着排，顺序、间距、圆角都和 Android 对齐。
struct HomeView: View {
    @Environment(AppSettings.self) private var settings
    @State private var todos = TodoStore.shared
    @State private var schedule = ScheduleStore.shared
    @State private var showCardPicker = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: WK.sectionSpacing) {
                ForEach(settings.commonTools) { tool in
                    NavigationLink(value: tool) {
                        card(for: tool)
                    }
                    .buttonStyle(WKPressStyle())
                }

                if settings.commonTools.isEmpty {
                    emptyCard
                }

                footer
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 6)
            .padding(.bottom, 28)
        }
        .background(WKWallpaperBackground())
        .scrollIndicators(.hidden)
        .navigationTitle("常用")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showCardPicker = true
                } label: {
                    Image(systemName: "square.grid.2x2")
                        .font(.system(size: 16, weight: .semibold))
                }
                .accessibilityLabel("常用卡片显示")
            }
        }
        .navigationDestination(for: WKTool.self) { tool in
            ToolDestination(tool: tool)
        }
        .sheet(isPresented: $showCardPicker) {
            CommonCardsView()
        }
    }

    // MARK: - 卡片

    @ViewBuilder
    private func card(for tool: WKTool) -> some View {
        switch tool {
        case .schedule:
            scheduleCard
        case .today:
            todayCard
        default:
            standardCard(tool)
        }
    }

    /// 课表卡：和 Android 一样多一段「正在上课 / 接下来」和课程大字。
    private var scheduleCard: some View {
        GlassCard(padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    ToolGlyph(tool: .schedule, size: 26)
                    Text("课表")
                        .font(.system(size: 17, weight: .medium))
                    Spacer(minLength: 8)
                    chevron
                }

                Text(focusStatus)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(focus?.ongoing == true ? settings.accentColor : Color.secondary)
                    .padding(.top, 16)

                Text(focus?.course.name ?? "没有课哦")
                    .font(.system(size: 24, weight: .bold))
                    .lineLimit(2)
                    .padding(.top, 4)

                Text(focusDetail)
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .padding(.top, 8)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 18)
        }
    }

    private var todayCard: some View {
        GlassCard {
            HStack(spacing: 14) {
                ToolGlyph(tool: .today, size: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text("今日")
                        .font(.system(size: 17, weight: .medium))
                    todaySummary
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                chevron
            }
        }
    }

    private func standardCard(_ tool: WKTool) -> some View {
        GlassCard {
            HStack(spacing: 14) {
                ToolGlyph(tool: tool, size: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(tool.title)
                        .font(.system(size: 17, weight: .medium))
                    Text(tool.summary)
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                chevron
            }
        }
    }

    private var emptyCard: some View {
        GlassCard {
            Text("常用还没有卡片，点右上角的按钮从「更多」里挑几个。")
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
        }
    }

    private var footer: some View {
        Text(
            settings.commonTools.isEmpty
                ? "更多工具可在「更多」页查看与管理"
                : "更多工具可在「更多」页查看与管理"
        )
        .font(.system(size: 12))
        .foregroundStyle(.tertiary)
        .frame(maxWidth: .infinity)
        .padding(.top, 6)
    }

    private var chevron: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(WKInk.tertiary)
    }

    // MARK: - 今日摘要

    private var todaySummary: Text {
        let todo = Text("\(todos.remainingCount)")
            .fontWeight(.semibold)
            .foregroundStyle(.primary)
        if !schedule.hasCourses {
            return todo + Text(" 项待办 · 课表未导入").foregroundStyle(.secondary)
        }
        let count = Text("\(todayCourses.count)")
            .fontWeight(.semibold)
            .foregroundStyle(.primary)
        return todo + Text(" 项待办 · ").foregroundStyle(.secondary) + count
            + Text(" 节课").foregroundStyle(.secondary)
    }

    // MARK: - 课表焦点

    private var todayCourses: [Course] {
        schedule.courses(on: WKDate.todayWeekday)
    }

    private var focus: (course: Course, ongoing: Bool)? {
        let now = Self.minutesSinceMidnight()
        for course in todayCourses {
            let start = Self.sectionMinutes(course.startSection, edge: .start, store: schedule) ?? 0
            let end = Self.sectionMinutes(course.endSection, edge: .end, store: schedule) ?? 0
            if now >= start && now <= end {
                return (course, true)
            }
        }
        for course in todayCourses {
            if let start = Self.sectionMinutes(course.startSection, edge: .start, store: schedule),
               start > now {
                return (course, false)
            }
        }
        return nil
    }

    private var focusStatus: String {
        guard let focus else { return "今天没有课了" }
        return focus.ongoing ? "正在上课" : "接下来"
    }

    private var focusDetail: String {
        guard let course = focus?.course else { return "好好休息吧！( ˘ω˘ )" }
        var parts: [String] = []
        let start = schedule.sections.first { $0.number == course.startSection }?.start ?? ""
        let end = schedule.sections.first { $0.number == course.endSection }?.end ?? ""
        if !start.isEmpty && !end.isEmpty {
            parts.append("\(start)–\(end)")
        }
        if !course.room.isEmpty {
            parts.append(course.room)
        }
        return parts.isEmpty ? "第 \(course.startSection)-\(course.endSection) 节" : parts.joined(separator: " · ")
    }

    private enum SectionEdge { case start, end }

    private static func sectionMinutes(_ number: Int, edge: SectionEdge, store: ScheduleStore) -> Int? {
        guard let section = store.sections.first(where: { $0.number == number }) else { return nil }
        let text = edge == .start ? section.start : section.end
        return minutes(from: text)
    }

    private static func minutes(from text: String) -> Int? {
        let parts = text.split(separator: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]) else { return nil }
        return hour * 60 + minute
    }

    private static func minutesSinceMidnight() -> Int {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: Date())
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }
}

#Preview {
    NavigationStack {
        HomeView()
    }
    .environment(AppSettings.shared)
}
