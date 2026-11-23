import Foundation

// MARK: - 成绩

struct GradeEntry: Identifiable, Hashable {
    var cjdm: String?
    var courseName: String
    var termCode: String
    var termName: String
    var scoreText: String?
    var scoreValue: Double?
    var point: Double?
    var credit: Double?
    var courseCode: String?
    var category: String?
    var studyMode: String?
    var scoreMode: String?
    var examNature: String?
    var department: String?
    var hours: Int?
    var remark: String?

    var id: String { "\(cjdm ?? "")|\(courseName)|\(termCode)|\(examNature ?? "")" }

    var passed: Bool? {
        if let scoreValue { return scoreValue >= 60 }
        if let point { return point >= 1.0 }
        return nil
    }

    /// 展示用成绩：百分制给数字，等级制给文本。
    var displayScore: String {
        if let scoreText, Double(scoreText) == nil { return scoreText }
        if let scoreValue {
            return scoreValue == scoreValue.rounded() ? String(Int(scoreValue)) : String(format: "%.1f", scoreValue)
        }
        return scoreText ?? "—"
    }
}

struct GradePart: Identifiable, Hashable {
    var name: String
    var weight: Double?
    var score: String?
    var id: String { name }
}

struct GradeRank: Identifiable, Hashable {
    var type: String
    var name: String
    var total: Int?
    var rank: Int?
    var id: String { "\(type)|\(name)" }
}

struct GradeDetail {
    var total: String?
    var parts: [GradePart]
    var ranks: [GradeRank]
}

struct GradeTerm: Identifiable, Hashable {
    var code: String
    var name: String
    var entries: [GradeEntry]
    var id: String { code }

    var totalCredit: Double {
        entries.reduce(0) { $0 + ($1.credit ?? 0) }
    }

    var gpa: Double? {
        var weight = 0.0
        var sum = 0.0
        for entry in entries {
            guard let credit = entry.credit, let point = entry.point else { continue }
            weight += credit
            sum += credit * point
        }
        return weight > 0 ? sum / weight : nil
    }
}

struct GradesData {
    var terms: [GradeTerm]
    var fetchedAt: Date = Date()

    var all: [GradeEntry] { terms.flatMap(\.entries) }
    var totalCredit: Double { all.reduce(0) { $0 + ($1.credit ?? 0) } }

    var overallGpa: Double? {
        var weight = 0.0
        var sum = 0.0
        for entry in all {
            guard let credit = entry.credit, let point = entry.point else { continue }
            weight += credit
            sum += credit * point
        }
        return weight > 0 ? sum / weight : nil
    }

    var earliestTermCode: String? { terms.first?.code }
}

// MARK: - 绩点

struct GpaRow: Identifiable, Hashable {
    var label: String
    var gpa: Double
    var id: String { label }
}

struct GpaData {
    var overall: GpaRow?
    var terms: [GpaRow]
    var fetchedAt: Date = Date()

    var best: GpaRow? { terms.max { $0.gpa < $1.gpa } }
}

// MARK: - 体测

struct PhysicalItem: Identifiable, Hashable {
    var name: String
    var value: String?
    var score: String?
    var level: String?
    var id: String { name }
}

struct PhysicalGroup: Identifiable, Hashable {
    var title: String
    var items: [PhysicalItem]
    var id: String { title }
}

struct PhysicalTest: Identifiable, Hashable {
    var termName: String
    var total: String?
    var level: String?
    var remark: String?
    var groups: [PhysicalGroup]
    var id: String { termName }
}

// MARK: - 教评

struct EvalTask: Identifiable, Hashable {
    var pjdm: String?
    var courseName: String
    var teacher: String?
    var session: String?
    var closeDate: String?
    var termCode: String
    var termName: String
    var id: String { "\(pjdm ?? "")|\(courseName)|\(teacher ?? "")|\(session ?? "")" }
}

struct EvalAnswer: Identifiable, Hashable {
    var question: String
    var answer: String?
    var score: String?
    var weight: String?
    var feedback: String?
    var id: String { question }
}

struct EvalDetail {
    var task: EvalTask
    var total: String?
    var answers: [EvalAnswer]
}

struct EvalTerm: Identifiable, Hashable {
    var code: String
    var name: String
    var tasks: [EvalTask]
    var id: String { code }
}

// MARK: - 考试

struct ExamEntry: Identifiable, Hashable {
    var courseName: String
    /// 只到天，没有具体日期时排到最后。
    var date: Date?
    var dateRaw: String?
    var startTime: String?
    var endTime: String?
    var sectionLabel: String?
    var room: String?
    var seat: String?
    var kind: String?
    var invigilators: String?
    var assessmentForm: String?
    var paperMode: String?
    var candidateCount: String?
    var remark: String?

    var id: String { "\(courseName)|\(dateRaw ?? "")|\(startTime ?? "")|\(room ?? "")" }

    var timeLabel: String? {
        switch (startTime, endTime) {
        case let (start?, end?): return "\(start) - \(end)"
        case let (start?, nil): return start
        case let (nil, end?): return end
        default: return nil
        }
    }

    var methodLabel: String? {
        let parts = [assessmentForm, paperMode].compactMap { $0 }.reduce(into: [String]()) { result, item in
            if !result.contains(item) { result.append(item) }
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

struct ExamData {
    var termCode: String
    var termName: String
    var entries: [ExamEntry]
    var fetchedAt: Date = Date()

    func nextFrom(_ today: Date) -> ExamEntry? {
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: today)
        return entries.first { entry in
            guard let date = entry.date else { return false }
            return calendar.startOfDay(for: date) >= start
        }
    }
}

// MARK: - 学期

enum TermCode {
    /// "202501" -> "2025-2026-1"
    static func displayName(_ code: String) -> String {
        guard code.count == 6, let year = Int(code.prefix(4)) else { return code }
        switch code.suffix(2) {
        case "01": return "\(year)-\(year + 1)-1"
        case "02": return "\(year)-\(year + 1)-2"
        default: return code
        }
    }

    /// 当前学期：1 月前算上一学年第二学期，2–7 月算第二学期，8 月起算第一学期。
    static func current() -> String {
        let calendar = Calendar.current
        let now = Date()
        let year = calendar.component(.year, from: now)
        let month = calendar.component(.month, from: now)
        switch month {
        case 2...7:
            return "\(year - 1)02"
        case 8...12:
            return "\(year)01"
        default:
            return "\(year - 1)01"
        }
    }

    static func all(from earliest: String, to current: String) -> [String] {
        guard let startYear = Int(earliest.prefix(4)),
              let endYear = Int(current.prefix(4)),
              let endHalf = Int(current.suffix(2))
        else { return [current] }
        var year = startYear
        var half = Int(earliest.suffix(2)) ?? 1
        var result: [String] = []
        while year < endYear || (year == endYear && half <= endHalf) {
            result.append(String(format: "%d%02d", year, half))
            if half == 1 { half = 2 } else { half = 1; year += 1 }
            if result.count > 16 { break }
        }
        return result.isEmpty ? [current] : result
    }
}
