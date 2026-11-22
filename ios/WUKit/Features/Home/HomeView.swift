import SwiftUI

struct HomeView: View {
    @State private var todos = TodoStore.shared
    @State private var schedule = ScheduleStore.shared
    @State private var route: WKTool?

    private let gridColumns = [
        GridItem(.flexible(), spacing: WK.sectionSpacing),
        GridItem(.flexible(), spacing: WK.sectionSpacing)
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                header
                todayCard
                scheduleCard
                grid
                Color.clear.frame(height: WK.bottomBarInset)
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 8)
        }
        .background(PageBackground())
        .scrollIndicators(.hidden)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(item: $route) { tool in
            ToolDestination(tool: tool)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("常用")
                .font(.system(size: 32, weight: .bold))
            Text(DateFormatter.wkToday.string(from: Date()))
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }

    private var todayCard: some View {
        GlassCardButton(action: { route = .today }) {
            HStack(spacing: 14) {
                ToolBadge(tool: .today)
                VStack(alignment: .leading, spacing: 4) {
                    Text("今日")
                        .font(.system(size: 17, weight: .semibold))
                    Text(todaySummary)
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                chevron
            }
        }
    }

    private var scheduleCard: some View {
        GlassCardButton(action: { route = .schedule }) {
            HStack(spacing: 14) {
                ToolBadge(tool: .schedule)
                VStack(alignment: .leading, spacing: 4) {
                    Text("课表")
                        .font(.system(size: 17, weight: .semibold))
                    Text(scheduleSummary)
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                chevron
            }
        }
    }

    private var grid: some View {
        LazyVGrid(columns: gridColumns, spacing: WK.sectionSpacing) {
            ForEach(quickTools) { tool in
                GlassCardButton(padding: 14, action: { route = tool }) {
                    VStack(alignment: .leading, spacing: 10) {
                        ToolBadge(tool: tool, size: 34)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tool.title)
                                .font(.system(size: 15, weight: .semibold))
                            Text(tool.summary)
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                }
            }
        }
    }

    private var chevron: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(.tertiary)
    }

    private var quickTools: [WKTool] {
        WKTool.defaultCommon.filter { $0 != .today && $0 != .schedule }
    }

    private var todaySummary: String {
        let count = schedule.courses(on: WKDate.todayWeekday).count
        if !schedule.hasCourses {
            return "课表未导入 · \(todos.remainingCount) 项待办"
        }
        return "\(todos.remainingCount) 项待办 · \(count) 节课"
    }

    private var scheduleSummary: String {
        schedule.hasCourses ? "本周 \(schedule.courses.count) 门课" : "课表未导入"
    }
}

#Preview {
    NavigationStack {
        HomeView()
    }
}
