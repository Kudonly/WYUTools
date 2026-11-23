import SwiftUI

/// 课表。版式对齐 Android 的 ScheduleScreen：顶部日期胶囊、左侧冻结节次轴、
/// 每周一页的横向 pager（一次只显示一周，只能左右滑动换周）、底部实色周滑块。
///
/// 所有横向位置都从同一套数算出来：`左右各 10 的 gutter + 42 的节次轴`，
/// 剩下的宽度按天均分。日期胶囊用完全相同的算式，所以日期永远压在列上。
struct ScheduleView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.colorScheme) private var scheme
    @State private var store = ScheduleStore.shared
    @State private var auth = AuthStore.shared

    @State private var showAdd = false
    @State private var showLogin = false
    @State private var showImportConfirm = false
    @State private var importing = false
    @State private var importMessage: String?
    @State private var detail: SelectedCourse?

    /// 界面上的当前周：胶囊、pager、滑块共用这一个值。
    @State private var selectedWeek: Int = ScheduleStore.shared.currentWeek
    /// pager 停住的页（周编号），横滑结束就同步回 selectedWeek。
    @State private var visibleWeek: Int? = ScheduleStore.shared.currentWeek

    // 尺寸照搬安卓：10 页边距、42 节次轴、64 行高、40 胶囊、16 圆角。
    private let gutter: CGFloat = 10
    private let axisWidth: CGFloat = 42
    private let rowHeight: CGFloat = 64
    private let capsuleHeight: CGFloat = 40
    private let capsuleTop: CGFloat = 8
    private let capsuleGap: CGFloat = 10
    private let weekBarHeight: CGFloat = 30
    private let weekBarGap: CGFloat = 3
    private let weekBarSide: CGFloat = 40
    private let pillWidth: CGFloat = 86

    private var dayCount: Int { settings.showWeekend ? 7 : 5 }
    private var sectionCount: Int { max(1, store.sections.count) }
    private var gridHeight: CGFloat { rowHeight * CGFloat(sectionCount) }

    var body: some View {
        GeometryReader { proxy in
            // 列区宽度 = 总宽 - 左 gutter - 节次轴 - 右 gutter。
            let columnsWidth = max(120, proxy.size.width - gutter * 2 - axisWidth)
            let columnWidth = columnsWidth / CGFloat(dayCount)

            ScrollView(.vertical) {
                VStack(spacing: 0) {
                    // 给悬浮的日期胶囊让位。
                    Color.clear.frame(height: capsuleTop + capsuleHeight + capsuleGap)

                    HStack(alignment: .top, spacing: 0) {
                        axisCard
                        weekPager(columnsWidth: columnsWidth, columnWidth: columnWidth)
                    }
                    .padding(.horizontal, gutter)

                    Color.clear.frame(height: weekBarHeight + weekBarGap + 10)
                }
            }
            .scrollIndicators(.hidden)
            .overlay(alignment: .top) {
                dateCapsule(columnsWidth: columnsWidth, columnWidth: columnWidth)
                    .padding(.horizontal, gutter)
                    .padding(.top, capsuleTop)
            }
            .overlay(alignment: .bottom) {
                weekBar
                    .padding(.horizontal, weekBarSide)
                    .padding(.bottom, weekBarGap)
            }
        }
        .background(WKWallpaperBackground())
        .navigationTitle("课表")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { toolbarMenu }
        }
        .overlay(alignment: .top) { noticeBar }
        .onAppear {
            selectedWeek = store.currentWeek
            visibleWeek = store.currentWeek
            CourseReminder.sync()
        }
        .onChange(of: visibleWeek) { _, new in
            guard let new, new != selectedWeek else { return }
            selectedWeek = min(max(new, 1), store.maxWeek)
            WKHaptics.selection()
        }
        .onChange(of: settings.showWeekend) { _, _ in
            selectedWeek = min(max(selectedWeek, 1), store.maxWeek)
            visibleWeek = selectedWeek
        }
        .onChange(of: store.courses) { _, _ in
            CourseReminder.sync()
            let week = min(max(selectedWeek, 1), store.maxWeek)
            if week != selectedWeek {
                selectedWeek = week
                visibleWeek = week
            }
        }
        .sheet(isPresented: $showAdd) {
            AddCourseView(store: store)
        }
        .sheet(item: $detail) { selection in
            CourseDetailSheet(course: selection.course, week: selection.week, store: store)
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
        }
        .confirmationDialog(
            "导入会替换当前 \(store.courses.count) 门课",
            isPresented: $showImportConfirm,
            titleVisibility: .visible
        ) {
            Button("替换并导入") { importSchedule() }
            Button("取消", role: .cancel) {}
        } message: {
            Text("导入的是本学期教务课表，手动添加的课程会被清掉。")
        }
    }

    // MARK: - 顶栏

    private var toolbarMenu: some View {
        Menu {
            Button {
                if auth.loggedIn {
                    if store.hasCourses { showImportConfirm = true } else { importSchedule() }
                } else {
                    showLogin = true
                }
            } label: {
                Label("从教务导入", systemImage: "arrow.down.doc")
            }

            Button {
                showAdd = true
            } label: {
                Label("手动添加课程", systemImage: "plus")
            }

            if store.hasCourses {
                Button(role: .destructive) {
                    store.replaceAll([])
                    importMessage = "已清空本课表。"
                } label: {
                    Label("清空课表", systemImage: "trash")
                }
            }

            Divider()

            Button {
                settings.showWeekend.toggle()
            } label: {
                Label(
                    settings.showWeekend ? "隐藏周末" : "显示周末",
                    systemImage: settings.showWeekend ? "checkmark.circle" : "circle"
                )
            }
        } label: {
            if importing {
                ProgressView().controlSize(.small)
            } else {
                Image(systemName: "ellipsis.circle")
                    .font(.system(size: 16, weight: .semibold))
            }
        }
        .accessibilityLabel("课表操作")
    }

    @ViewBuilder
    private var noticeBar: some View {
        if let importMessage {
            Text(importMessage)
                .font(.system(size: 12, weight: .medium))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(.regularMaterial, in: Capsule())
                .padding(.top, 56)
                .transition(.move(edge: .top).combined(with: .opacity))
                .task {
                    try? await Task.sleep(nanoseconds: 2_600_000_000)
                    withAnimation { self.importMessage = nil }
                }
        }
    }

    // MARK: - 日期胶囊

    /// 胶囊内部的列宽与网格完全一致：周数占住节次轴那 42，后面每天占一列。
    private func dateCapsule(columnsWidth: CGFloat, columnWidth: CGFloat) -> some View {
        HStack(spacing: 0) {
            Text("\(selectedWeek)周")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(selectedWeek == store.currentWeek ? settings.accentColor : Color.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(width: axisWidth, height: capsuleHeight)

            ForEach(1...dayCount, id: \.self) { day in
                let date = store.date(week: selectedWeek, day: day)
                let isToday = Calendar.wk.isDateInToday(date)
                VStack(spacing: 0) {
                    Text(WKDate.weekdayTitle(day))
                        .font(.system(size: 11, weight: .medium))
                    Text(monthDay(date))
                        .font(.system(size: 9))
                        .opacity(isToday ? 1 : 0.82)
                }
                .foregroundStyle(isToday ? settings.accentColor : Color.secondary)
                .frame(width: columnWidth, height: capsuleHeight)
            }
        }
        .frame(width: axisWidth + columnsWidth, height: capsuleHeight, alignment: .leading)
        .wkSurface(
            Capsule(),
            style: settings.cardStyle,
            strength: settings.glassStrength
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("第 \(selectedWeek) 周")
    }

    private func monthDay(_ date: Date) -> String {
        let parts = Calendar.wk.dateComponents([.month, .day], from: date)
        return "\(parts.month ?? 1)/\(parts.day ?? 1)"
    }

    // MARK: - 节次轴

    /// 安卓的轴卡在 42 宽里再留 1dp 边，卡片本身 40 宽。
    private var axisCard: some View {
        VStack(spacing: 0) {
            ForEach(store.sections) { section in
                VStack(spacing: 1) {
                    Text("\(section.number)")
                        .font(.system(size: 10, weight: .medium))
                    Text(section.start)
                        .font(.system(size: 8))
                    Text(section.end)
                        .font(.system(size: 8))
                }
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .frame(height: rowHeight)
            }
        }
        .frame(width: axisWidth - 2, height: gridHeight)
        .wkSurface(
            RoundedRectangle(cornerRadius: 16, style: .continuous),
            style: settings.cardStyle,
            strength: settings.glassStrength
        )
        .frame(width: axisWidth, alignment: .center)
        .zIndex(0)
    }

    // MARK: - 每周一页

    /// 一周一页。pager 正好等于列区宽度，一次只露一页，横滑整页切换。
    private func weekPager(columnsWidth: CGFloat, columnWidth: CGFloat) -> some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(1...store.maxWeek, id: \.self) { week in
                    weekPage(week, columnWidth: columnWidth)
                        .frame(width: columnsWidth, height: gridHeight, alignment: .topLeading)
                        .id(week)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $visibleWeek)
        .scrollIndicators(.hidden)
        .frame(width: columnsWidth, height: gridHeight)
        .zIndex(1)
    }

    private func weekPage(_ week: Int, columnWidth: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            horizontalRules(columnWidth: columnWidth)

            HStack(spacing: 0) {
                ForEach(1...dayCount, id: \.self) { day in
                    dayColumn(day, week: week, columnWidth: columnWidth, isLast: day == dayCount)
                }
            }
        }
        .frame(width: columnWidth * CGFloat(dayCount), height: gridHeight, alignment: .topLeading)
        .clipped()
    }

    /// 横向分隔线跨整周画一条，避免每列各画一段留下接缝。
    private func horizontalRules(columnWidth: CGFloat) -> some View {
        Path { path in
            guard sectionCount > 1 else { return }
            for index in 1..<sectionCount {
                let y = rowHeight * CGFloat(index) - 0.25
                path.move(to: CGPoint(x: 0, y: y))
                path.addLine(to: CGPoint(x: columnWidth * CGFloat(dayCount), y: y))
            }
        }
        .stroke(Color.primary.opacity(0.10), lineWidth: 0.5)
    }

    private func dayColumn(_ day: Int, week: Int, columnWidth: CGFloat, isLast: Bool) -> some View {
        let isToday = week == store.currentWeek && day == WKDate.todayWeekday

        return ZStack(alignment: .topLeading) {
            if isToday {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.primary.opacity(0.045))
                    .overlay {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(settings.accentColor.opacity(0.45), lineWidth: 1)
                    }
                    .frame(width: columnWidth, height: gridHeight)
            }

            if !isLast {
                Path { path in
                    let x = columnWidth - 0.25
                    path.move(to: CGPoint(x: x, y: 0))
                    path.addLine(to: CGPoint(x: x, y: gridHeight))
                }
                .stroke(Color.primary.opacity(0.10), lineWidth: 0.5)
            }

            ForEach(store.courses(on: day, week: week)) { course in
                courseCell(course, columnWidth: columnWidth)
            }
        }
        .frame(width: columnWidth, height: gridHeight, alignment: .topLeading)
    }

    /// 课程块左右各留 2、上下各留 2 —— 和安卓的 padding(horizontal = 2.dp)
    /// 加 offset(y = ... + 2.dp) 同一套留白，左边不会再贴住节次轴。
    private func courseCell(_ course: Course, columnWidth: CGFloat) -> some View {
        let start = min(max(course.startSection, 1), sectionCount)
        let end = min(max(course.endSection, start), sectionCount)
        let span = max(1, end - start + 1)

        return Button {
            detail = SelectedCourse(course: course, week: selectedWeek)
        } label: {
            courseBlock(course)
        }
        .buttonStyle(.plain)
        .frame(
            width: max(1, columnWidth - 4),
            height: max(1, rowHeight * CGFloat(span) - 4)
        )
        .offset(x: 2, y: rowHeight * CGFloat(start - 1) + 2)
    }

    private func courseBlock(_ course: Course) -> some View {
        let pair = CoursePalette.pair(course.colorIndex, dark: scheme == .dark)
        return VStack(alignment: .leading, spacing: 1) {
            Text(course.name)
                .font(.system(size: 10, weight: .semibold))
                .lineLimit(course.span >= 2 ? 4 : 2)
            if !course.room.isEmpty {
                Text(course.room)
                    .font(.system(size: 8))
                    .opacity(0.82)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
        }
        .foregroundStyle(pair.content)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .padding(.vertical, 3)
        .background(pair.background, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
    }

    // MARK: - 底部周滑块

    private var weekBar: some View {
        HStack(spacing: 8) {
            weekSlider
            currentWeekButton
        }
    }

    /// 实色滑块：不再套玻璃，轨道、进度条和圆点都是不透明填充。
    private var weekSlider: some View {
        GeometryReader { proxy in
            let thumb: CGFloat = 22
            let travel = max(1, proxy.size.width - thumb)
            let span = CGFloat(max(1, store.maxWeek - 1))
            let progress = min(max(CGFloat(selectedWeek - 1) / span, 0), 1)

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.primary.opacity(0.12))
                Capsule()
                    .fill(settings.accentColor)
                    .frame(width: thumb / 2 + travel * progress)
                Circle()
                    .fill(Color.white)
                    .frame(width: thumb, height: thumb)
                    .shadow(color: .black.opacity(0.18), radius: 3, y: 1)
                    .offset(x: travel * progress)
            }
            .frame(height: weekBarHeight)
            .contentShape(Capsule())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let x = min(max(value.location.x - thumb / 2, 0), travel)
                        let week = Int((x / travel * span).rounded()) + 1
                        select(week)
                    }
            )
        }
        .frame(height: weekBarHeight)
        .accessibilityElement()
        .accessibilityLabel("周次滑块")
        .accessibilityValue("第 \(selectedWeek) 周")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: select(selectedWeek + 1)
            case .decrement: select(selectedWeek - 1)
            @unknown default: break
            }
        }
    }

    private var currentWeekButton: some View {
        let isCurrent = selectedWeek == store.currentWeek
        return Button {
            select(store.currentWeek)
        } label: {
            Text(isCurrent ? "本周" : "返回本周")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(isCurrent ? Color.secondary : Color.primary)
                .frame(width: pillWidth, height: weekBarHeight)
                .background(Color.primary.opacity(isCurrent ? 0.05 : 0.09), in: Capsule())
                .overlay {
                    Capsule().strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.5)
                }
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(isCurrent)
        .accessibilityLabel(isCurrent ? "当前是本周" : "返回本周")
    }

    /// 选周：滑块、返回本周、pager 都走这里，三个控件永远同一个值。
    private func select(_ week: Int) {
        let clamped = min(max(week, 1), store.maxWeek)
        guard clamped != selectedWeek else { return }
        selectedWeek = clamped
        withAnimation(.snappy(duration: 0.22)) {
            visibleWeek = clamped
        }
        WKHaptics.selection()
    }

    // MARK: - 导入

    /// 拉当前学期课表并整体替换。
    private func importSchedule() {
        guard !importing else { return }
        importing = true
        importMessage = nil
        Task {
            do {
                let term = TermCode.current()
                let imported = try await ScheduleImporter.fetch(term: term)
                let courses = imported.map { item in
                    Course(
                        name: item.name,
                        teacher: item.teacher,
                        room: item.room,
                        weekday: item.weekday,
                        startSection: max(1, min(item.startSection, store.sections.count)),
                        endSection: max(1, min(item.endSection, store.sections.count)),
                        colorIndex: Self.colorIndex(for: item.name),
                        weeks: item.weeks
                    )
                }
                await MainActor.run {
                    store.replaceAll(courses)
                    selectedWeek = store.currentWeek
                    visibleWeek = store.currentWeek
                    importMessage = imported.isEmpty
                        ? "教务没有返回本学期课程。"
                        : "已导入 \(courses.count) 门课。"
                    WKHaptics.notify(.success)
                }
            } catch {
                await MainActor.run {
                    importMessage = auth.friendly(error)
                    if (error as? SsoError)?.sessionExpired == true {
                        auth.noteSessionExpired()
                    }
                    WKHaptics.notify(.error)
                }
            }
            await MainActor.run { importing = false }
        }
    }

    /// 同名课程每次都落到同一个颜色，换学期也不会整片变色。
    private static func colorIndex(for name: String) -> Int {
        var hash = 5381
        for scalar in name.unicodeScalars {
            hash = (hash &* 33) &+ Int(scalar.value)
        }
        return abs(hash) % 6
    }
}

/// 点开的课程块：周次、时间、地点一次说清。
private struct SelectedCourse: Identifiable {
    let course: Course
    let week: Int

    var id: UUID { course.id }
}

private struct CourseDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    let course: Course
    let week: Int
    let store: ScheduleStore

    var body: some View {
        NavigationStack {
            List {
                Section {
                    LabeledContent("课程", value: course.name)
                    if !course.teacher.isEmpty {
                        LabeledContent("教师", value: course.teacher)
                    }
                    if !course.room.isEmpty {
                        LabeledContent("地点", value: course.room)
                    }
                }

                Section {
                    LabeledContent("星期", value: "周\(WKDate.weekdayTitle(course.weekday))")
                    LabeledContent("节次", value: "第 \(course.startSection)-\(course.endSection) 节")
                    if let start = store.sections.first(where: { $0.number == course.startSection })?.start,
                       let end = store.sections.first(where: { $0.number == course.endSection })?.end {
                        LabeledContent("时间", value: "\(start)–\(end)")
                    }
                }

                Section {
                    LabeledContent("周次", value: weeksText)
                } footer: {
                    Text("查看的是第 \(week) 周的课表。")
                }
            }
            .navigationTitle("课程详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }

    /// "1-8, 10-16 周" 这种压缩写法，和安卓的 formatCourseWeeks 一致。
    private var weeksText: String {
        guard !course.weeks.isEmpty else { return "每周" }
        let sorted = course.weeks.sorted()
        var parts: [String] = []
        var start = sorted[0]
        var previous = sorted[0]
        for week in sorted.dropFirst() {
            if week == previous + 1 {
                previous = week
                continue
            }
            parts.append(start == previous ? "\(start)" : "\(start)-\(previous)")
            start = week
            previous = week
        }
        parts.append(start == previous ? "\(start)" : "\(start)-\(previous)")
        return parts.joined(separator: ", ") + " 周"
    }
}

struct AddCourseView: View {
    @Environment(\.dismiss) private var dismiss
    let store: ScheduleStore

    @State private var name = ""
    @State private var room = ""
    @State private var weekday = 1
    @State private var startSection = 1
    @State private var endSection = 2

    var body: some View {
        NavigationStack {
            Form {
                Section("课程") {
                    TextField("课程名", text: $name)
                    TextField("教室（可选）", text: $room)
                }

                Section("时间") {
                    Picker("星期", selection: $weekday) {
                        ForEach(1...7, id: \.self) { day in
                            Text("周\(WKDate.weekdayTitle(day))").tag(day)
                        }
                    }
                    Picker("开始节次", selection: $startSection) {
                        ForEach(store.sections) { section in
                            Text("第 \(section.number) 节").tag(section.number)
                        }
                    }
                    Picker("结束节次", selection: $endSection) {
                        ForEach(store.sections) { section in
                            Text("第 \(section.number) 节").tag(section.number)
                        }
                    }
                }
            }
            .navigationTitle("添加课程")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") { save() }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func save() {
        let start = min(startSection, endSection)
        let end = max(startSection, endSection)
        store.add(
            Course(
                name: name.trimmingCharacters(in: .whitespaces),
                room: room.trimmingCharacters(in: .whitespaces),
                weekday: weekday,
                startSection: start,
                endSection: end,
                colorIndex: Int.random(in: 0..<6)
            )
        )
        dismiss()
    }
}

#Preview {
    NavigationStack {
        ScheduleView()
    }
    .environment(AppSettings.shared)
}
