import Foundation
import Observation

/// 课表数据：节次轴 + 课程 + 周次锚点，全部存 UserDefaults。
@Observable
final class ScheduleStore {
    static let shared = ScheduleStore()

    private static let sectionsKey = "wukit.sections"
    private static let coursesKey = "wukit.courses"
    private static let anchorKey = "wukit.weekAnchor"
    private static let sectionsVersionKey = "wukit.sectionsVersion"
    /// 节次表版本。2 = 改用校方 14 节作息，老数据一次性升级。
    private static let sectionsVersion = 2

    private(set) var sections: [ScheduleSection] = ScheduleStore.defaultSections()
    private(set) var courses: [Course] = []
    /// 本学期第 1 周的周一。
    private(set) var anchor: WeekAnchor = ScheduleStore.defaultAnchor()

    init() {
        load()
    }

    static func defaultAnchor() -> WeekAnchor {
        let term = TermCode.current()
        return WeekAnchor(termCode: term, monday: ScheduleTerm.startMonday(termCode: term))
    }

    /// 与安卓同一份校方作息：14 节，第 13、14 节是中午时段，排在最后。
    static func defaultSections() -> [ScheduleSection] {
        let times: [(String, String)] = [
            ("08:15", "09:05"),
            ("09:05", "09:50"),
            ("10:10", "11:00"),
            ("11:00", "11:45"),
            ("14:30", "15:20"),
            ("15:20", "16:05"),
            ("16:25", "17:15"),
            ("17:15", "18:00"),
            ("19:30", "20:20"),
            ("20:20", "21:05"),
            ("21:20", "22:15"),
            ("22:15", "23:00"),
            ("12:30", "13:20"),
            ("13:20", "14:05")
        ]
        return times.enumerated().map { index, pair in
            ScheduleSection(number: index + 1, start: pair.0, end: pair.1)
        }
    }

    // MARK: - 周次

    /// 与安卓一致：至少 20 周，最多到课程覆盖到的周，封顶 30。
    var maxWeek: Int {
        let used = courses.reduce(0) { max($0, $1.weeks.max() ?? 0) }
        return min(30, max(20, used))
    }

    /// 今天是第几周；开学前算第 1 周。
    var currentWeek: Int { week(of: Date()) }

    func week(of date: Date) -> Int {
        let start = ScheduleTerm.monday(onOrBefore: date)
        let days = Calendar.wk.dateComponents([.day], from: anchor.monday, to: start).day ?? 0
        let week = Int((Double(days) / 7).rounded(.down)) + 1
        return min(max(week, 1), maxWeek)
    }

    /// 某一周第 day 天的日期（1 = 周一）。
    func date(week: Int, day: Int) -> Date {
        let offset = (week - 1) * 7 + (day - 1)
        return Calendar.wk.date(byAdding: .day, value: offset, to: anchor.monday) ?? anchor.monday
    }

    func courses(on weekday: Int, week: Int) -> [Course] {
        courses
            .filter { $0.weekday == weekday && $0.occurs(in: week) }
            .sorted { $0.startSection < $1.startSection }
    }

    var hasCourses: Bool { !courses.isEmpty }

    // MARK: - 编辑

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
        if defaults.integer(forKey: Self.sectionsVersionKey) < Self.sectionsVersion {
            // 老版本是 10 节、时间也不对；iOS 侧没有节次编辑器，直接换成校方作息。
            sections = Self.defaultSections()
            defaults.set(Self.sectionsVersion, forKey: Self.sectionsVersionKey)
            saveSections()
        } else if let data = defaults.data(forKey: Self.sectionsKey),
                  let decoded = try? JSONDecoder().decode([ScheduleSection].self, from: data),
                  !decoded.isEmpty {
            sections = decoded
        }
        if let data = defaults.data(forKey: Self.coursesKey),
           let decoded = try? JSONDecoder().decode([Course].self, from: data) {
            courses = decoded
        }
        let term = TermCode.current()
        if let data = defaults.data(forKey: Self.anchorKey),
           let decoded = try? JSONDecoder().decode(WeekAnchor.self, from: data),
           decoded.termCode == term {
            anchor = decoded
        } else {
            anchor = Self.defaultAnchor()
            saveAnchor()
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

    private func saveAnchor() {
        guard let data = try? JSONEncoder().encode(anchor) else { return }
        UserDefaults.standard.set(data, forKey: Self.anchorKey)
    }
}
