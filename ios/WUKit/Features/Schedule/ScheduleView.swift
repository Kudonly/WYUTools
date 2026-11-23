import SwiftUI

/// 课表。版式对齐 Android 的 ScheduleScreen：顶部日期胶囊、左侧冻结节次轴、
/// 每周一页的横向 pager（一次只显示一周，只能左右滑动换周）、底部周滑块。
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

    // 尺寸照搬安卓：64 行高、42 节次轴、16 圆角、10 页边距。
    private let rowHeight: CGFloat = 64
    private let axisWidth: CGFloat = 42
    private let pageGutter: CGFloat = 10
    private let cardCorner: CGFloat = 16
    private let capsuleHeight: CGFloat = 40
    private let weekBarHeight: CGFloat = 30
    private let pillWidth: CGFloat = 86

    private var dayCount: Int { settings.showWeekend ? 7 : 5 }
    private var sectionCount: Int { max(1, store.sections.count) }
    private var gridHeight: CGFloat { rowHeight * CGFloat(sectionCount) }

    var body: some View {
        GeometryReader { proxy in
            let pagerWidth = max(120, proxy.size.width - pageGutter - axisWidth)
            let columnWidth = max(28, (pagerWidth - pageGutter) / CGFloat(dayCount))

            ScrollView(.vertical) {
                VStack(spacing: 0) {
                    // 给胶囊让出位置：胶囊是浮层，网格从它下面开始。
                    Color.clear.frame(height: capsuleHeight + 16)

                    HStack(alignment: .top, spacing: 0) {
                        axisCard
                        weekPager(pagerWidth: pagerWidth, columnWidth: columnWidth)
                    }

                    Color.clear.frame(height: weekBarHeight + 24)
                }
                .padding(.leading, pageGutter)
            }
            .scrollIndicators(.hidden)
            .overlay(alignment: .top) {
                dateCapsule
                    .padding(.horizontal, 6)
                    .padding(.top, 4)
            }
            .overlay(alignment: .bottom) {
                weekBar
                    .padding(.horizontal, 40)
                    .padding(.bottom, 3)
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

    private var dateCapsule: some View {
        HStack(spacing: 0) {
            Text("\(selectedWeek)周")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(selectedWeek == store.currentWeek ? settings.accentColor : Color.primary)
                .lineLimit(1)
                .frame(width: axisWidth)

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
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 4)
        .frame(height: capsuleHeight)
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
        .frame(width: axisWidth)
        .wkSurface(
            RoundedRectangle(cornerRadius: cardCorner, style: .continuous),
            style: settings.cardStyle,
            strength: settings.glassStrength
        )
    }

    // MARK: - 每周一页

    /// 一周一页。pager 占满节次轴右侧，一次只露一页，横滑整页切换。
    private func weekPager(pagerWidth: CGFloat, columnWidth: CGFloat) -> some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(1...store.maxWeek, id: \.self) { week in
                    weekPage(week, columnWidth: columnWidth)
                        .frame(width: pagerWidth, height: gridHeight, alignment: .leading)
                        .id(week)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $visibleWeek)
        .scrollIndicators(.hidden)
        .frame(width: pagerWidth, height: gridHeight)
    }

    private func weekPage(_ week: Int, columnWidth: CGFloat) -> some View {
        let totalWidth = columnWidth * CGFloat(dayCount)
        return ZStack(alignment: .topLeading) {
            gridLines(columnWidth: columnWidth, totalWidth: totalWidth)

            if let index = todayColumn(week: week) {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.primary.opacity(0.045))
                    .overlay {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(settings.accentColor.opacity(0.45), lineWidth: 1)
                    }
                    .frame(width: columnWidth, height: gridHeight)
                    .offset(x: CGFloat(index) * columnWidth)
            }

            HStack(spacing: 0) {
                ForEach(1...dayCount, id: \.self) { day in
                    dayColumn(day, week: week, columnWidth: columnWidth)
                }
            }
        }
        .frame(width: totalWidth, height: gridHeight, alignment: .topLeading)
    }

    /// 今天落在这一页时返回列号（0 起）。周末隐藏时不算。
    private func todayColumn(week: Int) -> Int? {
        guard week == store.currentWeek else { return nil }
        let day = WKDate.todayWeekday
        guard day <= dayCount else { return nil }
        return day - 1
    }

    private func gridLines(columnWidth: CGFloat, totalWidth: CGFloat) -> some View {
        Path { path in
            if sectionCount > 1 {
                for index in 1..<sectionCount {
                    let y = rowHeight * CGFloat(index)
                    path.move(to: CGPoint(x: 0, y: y))
                    path.addLine(to: CGPoint(x: totalWidth, y: y))
                }
            }
            if dayCount > 1 {
                for index in 1..<dayCount {
                    let x = columnWidth * CGFloat(index)
                    path.move(to: CGPoint(x: x - 0.25, y: 0))
                    path.addLine(to: CGPoint(x: x - 0.25, y: gridHeight))
                }
            }
        }
        .stroke(Color.primary.opacity(0.10), lineWidth: 0.5)
    }

    private func dayColumn(_ day: Int, week: Int, columnWidth: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(store.courses(on: day, week: week)) { course in
                Button {
                    detail = SelectedCourse(course: course, week: week)
                } label: {
                    courseBlock(course)
                }
                .buttonStyle(.plain)
                .frame(width: columnWidth - 4, height: CGFloat(course.span) * rowHeight - 4)
                .offset(y: CGFloat(course.startSection - 1) * rowHeight + 2)
            }
        }
        .frame(width: columnWidth, height: gridHeight, alignment: .topLeading)
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

    private var weekSlider: some View {
        GeometryReader { proxy in
            let thumb: CGFloat = 22
            let travel = max(1, proxy.size.width - thumb)
            let span = CGFloat(max(1, store.maxWeek - 1))
            let progress = min(max(CGFloat(selectedWeek - 1) / span, 0), 1)

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(settings.accentColor.opacity(0.85))
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
        .wkSurface(
            Capsule(),
            style: settings.cardStyle,
            strength: settings.glassStrength
        )
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
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(isCurrent)
        .wkSurface(
            Capsule(),
            style: settings.cardStyle,
            strength: settings.glassStrength
        )
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
