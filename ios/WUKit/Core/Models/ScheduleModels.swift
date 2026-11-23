import Foundation

struct ScheduleSection: Identifiable, Codable, Hashable {
    var number: Int
    var start: String
    var end: String

    var id: Int { number }
}

/// 一门课。周次来自教务的 `zc` 字段，空集合表示每周都上。
struct Course: Identifiable, Codable, Hashable {
    var id: UUID
    var name: String
    var teacher: String
    var room: String
    /// 1 = 周一 … 7 = 周日
    var weekday: Int
    var startSection: Int
    var endSection: Int
    /// 0…5，对应课表调色板
    var colorIndex: Int
    var weeks: Set<Int>

    var span: Int { max(1, endSection - startSection + 1) }

    func occurs(in week: Int) -> Bool {
        weeks.isEmpty || weeks.contains(week)
    }

    init(
        id: UUID = UUID(),
        name: String,
        teacher: String = "",
        room: String = "",
        weekday: Int,
        startSection: Int,
        endSection: Int,
        colorIndex: Int = 0,
        weeks: Set<Int> = []
    ) {
        self.id = id
        self.name = name
        self.teacher = teacher
        self.room = room
        self.weekday = weekday
        self.startSection = startSection
        self.endSection = endSection
        self.colorIndex = colorIndex
        self.weeks = weeks
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case name
        case teacher
        case room
        case weekday
        case startSection
        case endSection
        case colorIndex
        case weeks
    }

    /// 旧版本存的课没有 weeks 字段：缺了就按「每周都上」读，不能让整份课表解不出来。
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        name = try container.decodeIfPresent(String.self, forKey: .name) ?? ""
        teacher = try container.decodeIfPresent(String.self, forKey: .teacher) ?? ""
        room = try container.decodeIfPresent(String.self, forKey: .room) ?? ""
        weekday = try container.decodeIfPresent(Int.self, forKey: .weekday) ?? 1
        startSection = try container.decodeIfPresent(Int.self, forKey: .startSection) ?? 1
        endSection = try container.decodeIfPresent(Int.self, forKey: .endSection) ?? startSection
        colorIndex = try container.decodeIfPresent(Int.self, forKey: .colorIndex) ?? 0
        weeks = try container.decodeIfPresent(Set<Int>.self, forKey: .weeks) ?? []
    }
}

/// 周次锚点：本学期的第 1 周周一。学期代码一变就重新推算。
struct WeekAnchor: Codable, Hashable {
    var termCode: String
    var monday: Date
}

enum ScheduleTerm {
    /// 第 1 周周一：第一学期取 9 月 1 日、第二学期取次年 3 月 1 日所在周的周一。
    static func startMonday(termCode: String) -> Date {
        let year = Int(termCode.prefix(4)) ?? Calendar.wk.component(.year, from: Date())
        let second = termCode.hasSuffix("02")
        var components = DateComponents()
        components.year = second ? year + 1 : year
        components.month = second ? 3 : 9
        components.day = 1
        let anchor = Calendar.wk.date(from: components) ?? Date()
        return monday(onOrBefore: anchor)
    }

    /// 这一天所在周的周一。
    static func monday(onOrBefore date: Date) -> Date {
        let start = Calendar.wk.startOfDay(for: date)
        // Calendar 的 weekday：1 是周日、2 是周一。
        let weekday = Calendar.wk.component(.weekday, from: start)
        let offset = (weekday + 5) % 7
        return Calendar.wk.date(byAdding: .day, value: -offset, to: start) ?? start
    }
}

extension Calendar {
    /// 周一为一周第一天的公历。周次计算都走它，免得受系统区域设置影响。
    static let wk: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2
        calendar.timeZone = .current
        return calendar
    }()
}
