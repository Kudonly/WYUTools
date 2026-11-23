import SwiftUI

/// 课表：左侧节次轴 + 七列网格，课程块按节次高度铺。
struct ScheduleView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = ScheduleStore.shared
    @State private var auth = AuthStore.shared
    @State private var showAdd = false
    @State private var showLogin = false
    @State private var showImportConfirm = false
    @State private var importing = false
    @State private var importMessage: String?

    private let rowHeight: CGFloat = 66
    private let axisWidth: CGFloat = 42
    private let columnSpacing: CGFloat = 4

    private var weekdays: [Int] {
        settings.showWeekend ? Array(1...7) : Array(1...5)
    }

    var body: some View {
        GeometryReader { proxy in
            let width = columnWidth(totalWidth: proxy.size.width)
            ScrollView([.horizontal, .vertical]) {
                VStack(spacing: 6) {
                    headerRow(width: width)
                    HStack(alignment: .top, spacing: columnSpacing) {
                        axis
                        ForEach(weekdays, id: \.self) { day in
                            dayColumn(day, width: width)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 40)
            }
        }
        .background(WKWallpaperBackground())
        .scrollIndicators(.hidden)
        .navigationTitle("课表")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
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
        }
        .overlay(alignment: .top) {
            if let importMessage {
                Text(importMessage)
                    .font(.system(size: 12, weight: .medium))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.top, 6)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task {
                        try? await Task.sleep(nanoseconds: 2_600_000_000)
                        withAnimation { self.importMessage = nil }
                    }
            }
        }
        .sheet(isPresented: $showAdd) {
            AddCourseView(store: store)
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
        .onChange(of: store.courses) { _, _ in
            CourseReminder.sync()
        }
    }

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
                        colorIndex: Self.colorIndex(for: item.name)
                    )
                }
                await MainActor.run {
                    store.replaceAll(courses)
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

    /// 同名课程每次导入都落到同一个颜色，换学期也不会整片变色。
    private static func colorIndex(for name: String) -> Int {
        var hash = 5381
        for scalar in name.unicodeScalars {
            hash = (hash &* 33) &+ Int(scalar.value)
        }
        return abs(hash) % 8
    }

    /// 每列宽度按屏幕摊开，不硬编码设备宽度。
    private func columnWidth(totalWidth: CGFloat) -> CGFloat {
        let count = CGFloat(weekdays.count)
        let usable = totalWidth - 24 - axisWidth - columnSpacing * count
        return max(58, usable / count)
    }

    private func headerRow(width: CGFloat) -> some View {
        HStack(spacing: columnSpacing) {
            Color.clear.frame(width: axisWidth, height: 26)
            ForEach(weekdays, id: \.self) { day in
                Text("周\(WKDate.weekdayTitle(day))")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(day == WKDate.todayWeekday ? settings.accentColor : Color.secondary)
                    .frame(width: width, height: 26)
            }
        }
    }

    private var axis: some View {
        VStack(spacing: 0) {
            ForEach(store.sections) { section in
                VStack(spacing: 1) {
                    Text("\(section.number)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.secondary)
                    if !section.start.isEmpty {
                        Text(section.start)
                            .font(.system(size: 9))
                            .foregroundStyle(.tertiary)
                    }
                }
                .frame(width: axisWidth, height: rowHeight)
            }
        }
    }

    private func dayColumn(_ weekday: Int, width: CGFloat) -> some View {
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                ForEach(store.sections) { _ in
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color.primary.opacity(0.04))
                        .frame(height: rowHeight)
                        .padding(.vertical, 1)
                }
            }

            ForEach(store.courses(on: weekday)) { course in
                courseBlock(course)
                    .frame(height: CGFloat(course.span) * rowHeight - 4)
                    .offset(y: CGFloat(course.startSection - 1) * rowHeight + 2)
            }
        }
        .frame(width: width)
    }

    private func courseBlock(_ course: Course) -> some View {
        let color = CoursePalette.color(course.colorIndex)
        return VStack(alignment: .leading, spacing: 2) {
            Text(course.name)
                .font(.system(size: 11, weight: .semibold))
                .lineLimit(2)
            if !course.room.isEmpty {
                Text(course.room)
                    .font(.system(size: 9))
                    .opacity(0.85)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(color.opacity(0.18), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(color)
                .frame(width: 3)
                .padding(.vertical, 8)
        }
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(color.opacity(0.22), lineWidth: 0.6)
        }
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
                colorIndex: Int.random(in: 0..<8)
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
