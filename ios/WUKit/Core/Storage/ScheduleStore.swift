import Foundation
import Observation

/// 课表数据：10 节时间轴 + 课程列表，存 UserDefaults。
/// 教务导入（jxgl）在 P1 接进来，先保证手工添加可用。
@Observable
final class ScheduleStore {
    static let shared = ScheduleStore()

    private static let sectionsKey = "wukit.sections"
    private static let coursesKey = "wukit.courses"

    private(set) var sections: [ScheduleSection] = ScheduleStore.defaultSections()
    private(set) var courses: [Course] = []

    init() {
        load()
    }

    static func defaultSections() -> [ScheduleSection] {
        let times: [(String, String)] = [
            ("08:00", "08:45"),
            ("08:55", "09:40"),
            ("10:00", "10:45"),
            ("10:55", "11:40"),
            ("14:30", "15:15"),
            ("15:25", "16:10"),
            ("16:30", "17:15"),
            ("17:25", "18:10"),
            ("19:30", "20:15"),
            ("20:25", "21:10")
        ]
        return times.enumerated().map { index, pair in
            ScheduleSection(number: index + 1, start: pair.0, end: pair.1)
        }
    }

    func courses(on weekday: Int) -> [Course] {
        courses
            .filter { $0.weekday == weekday }
            .sorted { $0.startSection < $1.startSection }
    }

    var hasCourses: Bool { !courses.isEmpty }

    func add(_ course: Course) {
        courses.append(course)
        saveCourses()
    }

    func remove(_ course: Course) {
        courses.removeAll { $0.id == course.id }
        saveCourses()
    }

    /// 教务导入成功后整体替换本课表。
    func replaceAll(_ newCourses: [Course]) {
        courses = newCourses
        saveCourses()
    }

    func updateSection(number: Int, start: String, end: String) {
        guard let index = sections.firstIndex(where: { $0.number == number }) else { return }
        sections[index].start = start
        sections[index].end = end
        saveSections()
    }

    func setSectionCount(_ count: Int) {
        let clamped = max(1, min(16, count))
        if clamped > sections.count {
            let defaults = Self.defaultSections()
            for number in (sections.count + 1)...clamped {
                if let template = defaults.first(where: { $0.number == number }) {
                    sections.append(template)
                } else {
                    sections.append(ScheduleSection(number: number, start: "", end: ""))
                }
            }
        } else if clamped < sections.count {
            sections = Array(sections.prefix(clamped))
        }
        saveSections()
    }

    // MARK: - Storage

    private func load() {
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: Self.sectionsKey),
           let decoded = try? JSONDecoder().decode([ScheduleSection].self, from: data),
           !decoded.isEmpty {
            sections = decoded
        }
        if let data = defaults.data(forKey: Self.coursesKey),
           let decoded = try? JSONDecoder().decode([Course].self, from: data) {
            courses = decoded
        }
    }

    private func saveSections() {
        guard let data = try? JSONEncoder().encode(sections) else { return }
        UserDefaults.standard.set(data, forKey: Self.sectionsKey)
    }

    private func saveCourses() {
        guard let data = try? JSONEncoder().encode(courses) else { return }
        UserDefaults.standard.set(data, forKey: Self.coursesKey)
    }
}
