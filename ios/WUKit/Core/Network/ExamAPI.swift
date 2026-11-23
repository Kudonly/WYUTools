import Foundation

/// 教务「学生考试任务」接口（`xsksrw/paginateXsksrw`）→ ExamData。
/// 字段名沿用强智那套拼音缩写，每个值按候选键依次取，日期时间从字符串里抠。
enum ExamAPI {
    static func fetch(term: String) async throws -> ExamData {
        let raw = try await SSOClient.shared.fetchJxglExams(termCode: term)
        return try parse(raw, term: term)
    }

    static func parse(_ raw: String, term: String) throws -> ExamData {
        let body = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { throw SsoError(message: "考试数据为空，请稍后重试。") }
        guard let root = JSON.object(body) else {
            throw SsoError(message: "考试数据格式异常，请稍后重试。")
        }
        let code = (root.string("code") ?? "").trimmingCharacters(in: .whitespaces)
        if code == "-401" || code == "401" {
            throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
        }

        var rows: [[String: Any]] = []
        for key in ["rows", "data", "list"] {
            if let array = root[key] as? [[String: Any]] { rows = array; break }
        }
        if rows.isEmpty {
            let message = ["msg", "message", "error"]
                .compactMap { root.string($0) }
                .first
            if let message { throw SsoError(message: message) }
        }

        var entries = rows.compactMap(mapRow)
        entries.sort { lhs, rhs in
            if (lhs.date == nil) != (rhs.date == nil) { return lhs.date != nil }
            if let l = lhs.date, let r = rhs.date, l != r { return l < r }
            if (lhs.startTime ?? "") != (rhs.startTime ?? "") {
                return (lhs.startTime ?? "") < (rhs.startTime ?? "")
            }
            return lhs.courseName < rhs.courseName
        }

        return ExamData(
            termCode: term,
            termName: TermCode.displayName(term),
            entries: entries
        )
    }

    private static func mapRow(_ row: [String: Any]) -> ExamEntry? {
        var fields: [String: String] = [:]
        for (key, value) in row {
            guard let text = JSON.text(value) else { continue }
            fields[key.lowercased()] = text
        }
        func pick(_ keys: String...) -> String? {
            for key in keys {
                if let value = fields[key.lowercased()], !value.isEmpty { return value }
            }
            return nil
        }

        guard let course = pick("kcmc", "kcmc_cn", "coursename", "course") else { return nil }
        let timeText = pick("kssj", "kssj_str", "examtime", "time", "kssjformat")
        let dateText = pick("ksrq", "ksrq_str", "examdate", "date") ?? timeText
        let (start, end) = timeRange(timeText)
        let section = sectionLabel(pick("jcdm2", "jcmc", "jcdm", "jc"))
            ?? timeText.flatMap { $0.contains("节") ? $0 : nil }

        var roomParts: [String] = []
        if let campus = pick("xqmc", "campusName") { roomParts.append(campus) }
        if let room = pick("kscdmc", "kscd", "ksdd", "cdmc", "room"), !roomParts.contains(room) {
            roomParts.append(room)
        }

        return ExamEntry(
            courseName: course,
            date: parseDate(dateText),
            dateRaw: dateText,
            startTime: start,
            endTime: end,
            sectionLabel: section,
            room: roomParts.isEmpty ? nil : roomParts.joined(separator: " · "),
            seat: pick("zwh", "zw", "zwmc", "seatno", "seat"),
            kind: pick("kslbmc", "kslxmc", "ksaplxmc", "examtype", "kslx"),
            invigilators: pick("jkteaxms", "jkteaxm", "jkjsxm", "teacher"),
            assessmentForm: pick("khxsmc", "khxs"),
            paperMode: pick("ksxsmc", "ksxs"),
            candidateCount: pick("ksrs", "xs"),
            remark: pick("bz", "remark", "memo")
        )
    }

    private static func parseDate(_ raw: String?) -> Date? {
        guard let raw, !raw.isEmpty else { return nil }
        guard let matched = raw.firstMatch(#"(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})"#) else {
            return nil
        }
        let numbers = matched.components(separatedBy: CharacterSet.decimalDigits.inverted)
            .filter { !$0.isEmpty }
        guard numbers.count >= 3,
              let year = Int(numbers[0]), let month = Int(numbers[1]), let day = Int(numbers[2])
        else { return nil }
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        return Calendar.current.date(from: components)
    }

    private static func timeRange(_ raw: String?) -> (String?, String?) {
        guard let raw, !raw.isEmpty else { return (nil, nil) }
        let times = raw.matches(#"(\d{1,2})\s*[:：]\s*(\d{2})"#).map { chunk -> String in
            let numbers = chunk.components(separatedBy: CharacterSet.decimalDigits.inverted)
                .filter { !$0.isEmpty }
            guard numbers.count >= 2, let hour = Int(numbers[0]) else { return chunk }
            return String(format: "%02d:%@", hour, numbers[1])
        }
        guard !times.isEmpty else { return (nil, nil) }
        return (times[0], times.count > 1 ? times[1] : nil)
    }

    private static func sectionLabel(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count == 4, trimmed.allSatisfy(\.isNumber) {
            let from = Int(trimmed.prefix(2)) ?? 0
            let to = Int(trimmed.suffix(2)) ?? 0
            return from == to ? "第 \(from) 节" : "第 \(from)-\(to) 节"
        }
        return trimmed.isEmpty ? nil : trimmed
    }
}
