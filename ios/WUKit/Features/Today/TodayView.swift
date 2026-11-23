import SwiftUI

/// 今日：待办清单 + 今天的课。版式跟 Android 今日页一致，只是换成原生控件。
struct TodayView: View {
    @Environment(AppSettings.self) private var settings
    @State private var todos = TodoStore.shared
    @State private var schedule = ScheduleStore.shared
    @State private var draft = ""

    private var todayCourses: [Course] {
        schedule.courses(on: WKDate.todayWeekday, week: schedule.currentWeek)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                header
                todoCard
                courseSection
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 4)
            .padding(.bottom, 28)
        }
        .background(WKWallpaperBackground())
        .scrollIndicators(.hidden)
        .navigationTitle("今日")
        .wkFullScreenDetail()
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(DateFormatter.wkToday.string(from: Date()))
                .font(.system(size: 26, weight: .bold))
            Text("周\(WKDate.weekdayTitle(WKDate.todayWeekday)) · \(todayCourses.count) 节课")
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }

    private var todoCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("待办")
                        .font(.system(size: 17, weight: .semibold))
                    Spacer()
                    if !todos.items.isEmpty {
                        Text("完成 \(todos.doneCount)/\(todos.items.count)")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                }

                HStack(spacing: 10) {
                    TextField("写点今天要做的事", text: $draft)
                        .textFieldStyle(.plain)
                        .submitLabel(.done)
                        .onSubmit(addTodo)
                        .padding(.horizontal, 12)
                        .frame(height: 40)
                        .background(
                            Color.primary.opacity(0.05),
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                        )

                    Button(action: addTodo) {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 40, height: 40)
                            .background(settings.accentColor, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                    .opacity(draft.trimmingCharacters(in: .whitespaces).isEmpty ? 0.4 : 1)
                }

                if todos.items.isEmpty {
                    Text("今天还空着，加一条试试。")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                        .padding(.top, 2)
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(todos.items.enumerated()), id: \.element.id) { index, item in
                            todoRow(item)
                            if index < todos.items.count - 1 {
                                WKDivider().padding(.leading, 30)
                            }
                        }
                    }
                }
            }
        }
    }

    private var courseSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("今天的课")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
                .padding(.top, 2)

            if todayCourses.isEmpty {
                GlassCard {
                    Text(schedule.hasCourses ? "今天没课。" : "还没有课表数据，去课表页添加课程。")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
            } else {
                ForEach(todayCourses) { course in
                    GlassCard(padding: 14) {
                        courseRow(course)
                    }
                }
            }
        }
    }

    private func todoRow(_ item: TodoItem) -> some View {
        HStack(spacing: 10) {
            Button {
                todos.toggle(item)
            } label: {
                Image(systemName: item.done ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20))
                    .foregroundStyle(item.done ? settings.accentColor : Color.secondary)
            }
            .buttonStyle(.plain)

            Text(item.title)
                .font(.system(size: 15))
                .strikethrough(item.done)
                .foregroundStyle(item.done ? Color.secondary : Color.primary)

            Spacer(minLength: 8)

            Button {
                todos.remove(item)
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.tertiary)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 6)
    }

    private func courseRow(_ course: Course) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(CoursePalette.color(course.colorIndex))
                .frame(width: 4, height: 38)

            VStack(alignment: .leading, spacing: 3) {
                Text(course.name)
                    .font(.system(size: 15, weight: .semibold))
                HStack(spacing: 8) {
                    Text(sectionTimeText(course))
                    if !course.room.isEmpty {
                        Text(course.room)
                    }
                }
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            }

            Spacer(minLength: 8)
        }
    }

    private func sectionTimeText(_ course: Course) -> String {
        let start = schedule.sections.first { $0.number == course.startSection }?.start ?? ""
        let end = schedule.sections.first { $0.number == course.endSection }?.end ?? ""
        if start.isEmpty || end.isEmpty {
            return "第 \(course.startSection)-\(course.endSection) 节"
        }
        return "\(start)–\(end)"
    }

    private func addTodo() {
        todos.add(draft)
        draft = ""
    }
}

#Preview {
    NavigationStack {
        TodayView()
    }
    .environment(AppSettings.shared)
}
