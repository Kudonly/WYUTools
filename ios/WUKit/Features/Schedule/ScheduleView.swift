import SwiftUI

struct ScheduleView: View {
    @State private var store = ScheduleStore.shared
    @State private var showAdd = false

    private let rowHeight: CGFloat = 64
    private let axisWidth: CGFloat = 44
    private let columnWidth: CGFloat = 96

    private var weekdays: [Int] { Array(1...7) }

    var body: some View {
        ScrollView([.horizontal, .vertical]) {
            VStack(spacing: 6) {
                headerRow
                HStack(alignment: .top, spacing: 4) {
                    axis
                    ForEach(weekdays, id: \.self) { day in
                        dayColumn(day)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 40)
        }
        .background(PageBackground())
        .navigationTitle("课表")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAdd = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showAdd) {
            AddCourseView(store: store)
        }
    }

    private var headerRow: some View {
        HStack(spacing: 4) {
            Color.clear.frame(width: axisWidth, height: 28)
            ForEach(weekdays, id: \.self) { day in
                Text("周\(WKDate.weekdayTitle(day))")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(day == WKDate.todayWeekday ? WKPalette.accent : Color.secondary)
                    .frame(width: columnWidth, height: 28)
            }
        }
    }

    private var axis: some View {
        VStack(spacing: 0) {
            ForEach(store.sections) { section in
                VStack(spacing: 2) {
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

    private func dayColumn(_ weekday: Int) -> some View {
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                ForEach(store.sections) { _ in
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color.primary.opacity(0.035))
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
        .frame(width: columnWidth)
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
        .background(color.opacity(0.20), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(color)
                .frame(width: 3)
                .padding(.vertical, 6)
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
}
