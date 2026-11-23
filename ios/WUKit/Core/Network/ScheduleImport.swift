import Foundation

/// 教务课表导入。返回课程列表由 `ScheduleStore` 合并进本课表。
enum ScheduleImporter {
    struct ImportedCourse: Hashable {
        var name: String
        var teacher: String
        var room: String
        var weekday: Int
        var startSection: Int
        var endSection: Int
        var weeks: Set<Int>
    }

    static func fetch(term: String) async throws -> [ImportedCourse] {
        let raw = try await SSOClient.shared.fetchJxglSchedule(termCode: term)
        return try parse(raw)
    }

    static func parse(_ raw: String) throws -> [ImportedCourse] {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw SsoError(message: "课表数据为空，请稍后重试。") }
        if text.hasPrefix("<") {
            if text.contains("login-form") || text.contains("pwdEncryptSalt") {
                throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
            }
            throw SsoError(message: "教务系统返回了异常页面，请稍后重试。")
        }
        guard let root = JSON.object(text) else {
            throw SsoError(message: "课表数据格式异常，请稍后重试。")
        }
        if let code = root.int("code"), code < 0 {
            if code == -401 {
                throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
            }
            let message = root.string("message") ?? root.string("msg") ?? "课表查询失败（\(code)）。"
            throw SsoError(message: message)
        }
        if root.bool("success") == false {
            throw SsoError(message: root.string("message") ?? root.string("msg") ?? "课表查询失败。")
        }

        var rows: [[String: Any]] = []
        if let array = root["data"] as? [[String: Any]] {
            rows = array
        } else if let array = root["rows"] as? [[String: Any]] {
            rows = array
        } else if let data = root["data"] as? [String: Any] {
            if data.string("kcmc") != nil {
                rows = [data]
            } else {
                for key in ["list", "rows", "records", "data"] {
                    if let array = data[key] as? [[String: Any]] { rows = array; break }
                }
            }
        }
        guard !rows.isEmpty else { return [] }

        var courses: [ImportedCourse] = []
        for row in rows {
            guard let name = row.string("kcmc") ?? row.string("kcName") ?? row.string("courseName") else { continue }
            guard let weekday = resolveDay(row) else { continue }
            guard let range = resolveSections(row) else { continue }
            let weeks = parseWeeks(row["zc"]) ?? parseWeeks(row["weeks"]) ?? parseWeeks(row["week"]) ?? Set(1...20)
            let room = resolveRoom(row, weeks: weeks)
            let teacher = row.string("teaxms") ?? row.string("teaxm") ?? row.string("jsxm") ?? ""
            courses.append(
                ImportedCourse(
                    name: name,
                    teacher: teacher,
                    room: room,
                    weekday: weekday,
                    startSection: range.0,
                    endSection: range.1,
                    weeks: weeks
                )
            )
        }
        return merge(courses)
    }

    // MARK: - 字段

    private static let dayTextKeys = ["xq", "jsxq", "qsxq", "day", "weekday", "xqmc", "jsxqmc"]
    private static let dayAliases = ["qsxq", "day", "weekday"]

    private static func resolveDay(_ row: [String: Any]) -> Int? {
        for key in dayTextKeys {
            if let text = row.string(key), let day = chineseDay(text) { return day }
        }
        if let value = Int(row.string("xq") ?? ""), (1...7).contains(value) { return value }
        if let value = Int(row.string("jsxq") ?? ""), (1...7).contains(value) {
            return value == 1 ? 7 : value - 1
        }
        for key in dayAliases {
            if let value = Int(row.string(key) ?? ""), (1...7).contains(value) {
                return value == 1 ? 7 : value - 1
            }
        }
        return nil
    }

    private static func chineseDay(_ raw: String) -> Int? {
        guard raw.contains("周") || raw.contains("星期") || raw.count == 1 else { return nil }
        if raw.contains("一") { return 1 }
        if raw.contains("二") { return 2 }
        if raw.contains("三") { return 3 }
        if raw.contains("四") { return 4 }
        if raw.contains("五") { return 5 }
        if raw.contains("六") { return 6 }
        if raw.contains("日") || raw.contains("天") { return 7 }
        return nil
    }

    private static func resolveSections(_ row: [String: Any]) -> (Int, Int)? {
        if let list = sectionField(row["jcdm2"]) { return (list.first!, list.last!) }
        if let list = sectionField(row["jcdm"]) { return (list.first!, list.last!) }
        let ps = row.int("ps") ?? 0
        let pe = row.int("pe") ?? 0
        if ps > 0 {
            let end = pe >= ps ? pe : ps
            return normalize(ps, end)
        }
        return nil
    }

    private static func sectionField(_ value: Any?) -> [Int]? {
        guard let value else { return nil }
        if let array = value as? [Any] {
            let numbers = array.compactMap { JSON.text($0).flatMap(Int.init) }.filter { (1...30).contains($0) }
            let unique = Array(Set(numbers)).sorted()
            return unique.isEmpty ? nil : unique
        }
        guard let text = JSON.text(value) else { return nil }
        if text.count == 4, text.allSatisfy(\.isNumber) {
            let start = Int(text.prefix(2)) ?? 0
            let end = Int(text.suffix(2)) ?? 0
            if start > 0, end >= start, end <= 30 { return Array(start...end) }
        }
        var numbers: [Int] = []
        // 同上：取整段匹配，不能取 group 1（那只会拿到起始节次）。
        for match in text.matches(#"(\d+)\s*[-~至—－]\s*(\d+)"#, group: 0) {
            let parts = match.components(separatedBy: CharacterSet.decimalDigits.inverted)
                .compactMap(Int.init)
            if parts.count >= 2, (1...30).contains(parts[0]), parts[1] >= parts[0], parts[1] <= 30 {
                numbers.append(contentsOf: parts[0]...parts[1])
            }
        }
        if numbers.isEmpty {
            numbers = text.matches(#"\d+"#, group: 0).compactMap(Int.init).filter { (1...30).contains($0) }
        }
        let unique = Array(Set(numbers)).sorted()
        return unique.isEmpty ? nil : unique
    }

    private static func normalize(_ start: Int, _ end: Int) -> (Int, Int)? {
        guard (1...30).contains(start), (1...30).contains(end) else { return nil }
        let low = min(start, end)
        let high = max(start, end)
        return high - low > 12 ? (low, low) : (low, high)
    }

    /// 解析教务的 `zc`（上课周次）。
    ///
    /// 注意 `matches` 的默认 group 是 1（取捕获组），而 `\d+` 这类模式没有捕获组，
    /// 取 group 1 会越界返回空数组 —— 之前所有课都退化成「每周都上」就是栽在这里。
    /// 这里一律显式取整段匹配（group 0）。
    private static func parseWeeks(_ value: Any?) -> Set<Int>? {
        guard let value else { return nil }
        if let array = value as? [Any] {
            let weeks = array.compactMap { JSON.text($0).flatMap(Int.init) }.filter { (1...30).contains($0) }
            return weeks.isEmpty ? nil : Set(weeks)
        }
        guard let raw = JSON.text(value) else { return nil }
        // 20 位以上的 0/1 串：第 n 位为 1 表示第 n 周上课。
        if raw.count >= 8, raw.allSatisfy({ $0 == "0" || $0 == "1" }) {
            var weeks = Set<Int>()
            for (index, char) in raw.enumerated() where char == "1" {
                if (1...30).contains(index + 1) { weeks.insert(index + 1) }
            }
            return weeks.isEmpty ? nil : weeks
        }
        guard raw.rangeOfCharacter(from: .decimalDigits) != nil else { return nil }

        // “1-16单周”：先按单双周过滤，和安卓的 parity 处理一致。
        let parity: Int
        if raw.contains("单") {
            parity = 1
        } else if raw.contains("双") {
            parity = 0
        } else {
            parity = -1
        }

        var weeks = Set<Int>()
        for chunk in raw.matches(#"(\d+)\s*[-~至—－]\s*(\d+)"#, group: 0) {
            let parts = chunk.components(separatedBy: CharacterSet.decimalDigits.inverted).compactMap(Int.init)
            guard parts.count >= 2, (1...30).contains(parts[0]), parts[1] >= parts[0] else { continue }
            for week in parts[0]...min(30, parts[1]) where parity < 0 || week % 2 == parity {
                weeks.insert(week)
            }
        }
        if weeks.isEmpty {
            for week in raw.matches(#"\d+"#, group: 0).compactMap(Int.init) where (1...30).contains(week) {
                if parity < 0 || week % 2 == parity { weeks.insert(week) }
            }
        }
        return weeks.isEmpty ? nil : weeks
    }

    private static func resolveRoom(_ row: [String: Any], weeks: Set<Int>) -> String {
        if let detailed = row.string("jxcdmc2") {
            var byWeek: [Int: String] = [:]
            for token in detailed.components(separatedBy: CharacterSet(charactersIn: ",，;；")) {
                let text = token.trimmingCharacters(in: .whitespaces)
                guard let dash = text.lastIndex(of: "-") else { continue }
                let weekText = text[text.index(after: dash)...].trimmingCharacters(in: .whitespaces)
                guard let week = Int(weekText.replacingOccurrences(of: "周", with: "")),
                      (1...30).contains(week), weeks.contains(week)
                else { continue }
                byWeek[week] = String(text[..<dash]).trimmingCharacters(in: .whitespaces)
            }
            let names = byWeek.values.filter { !$0.isEmpty }
            if let most = mostCommon(names) { return most }
        }
        let raw = row.string("jxcdmc") ?? row.string("zdjxcdmc") ?? row.string("cdmc") ?? ""
        return raw.trimmingCharacters(in: .whitespaces)
    }

    private static func mostCommon(_ values: [String]) -> String? {
        var counts: [String: Int] = [:]
        for value in values { counts[value, default: 0] += 1 }
        return counts.max { $0.value < $1.value }?.key
    }

    /// 同名同天同节次的课合并，教室/老师取并集。
    private static func merge(_ courses: [ImportedCourse]) -> [ImportedCourse] {
        var result: [ImportedCourse] = []
        for course in courses {
            // 教师也进合并键：同一门课不同老师、不同周次是两条课，
            // 只按名字合并会把两段周次并成一条，看起来就像「这门课每周都上」。
            if let index = result.firstIndex(where: {
                $0.name == course.name && $0.teacher == course.teacher
                    && $0.weekday == course.weekday
                    && $0.startSection == course.startSection && $0.endSection == course.endSection
            }) {
                if !result[index].room.contains(course.room), !course.room.isEmpty {
                    result[index].room = result[index].room.isEmpty
                        ? course.room
                        : result[index].room + " / " + course.room
                }
                if !result[index].teacher.contains(course.teacher), !course.teacher.isEmpty {
                    result[index].teacher = result[index].teacher.isEmpty
                        ? course.teacher
                        : result[index].teacher + "、" + course.teacher
                }
                result[index].weeks.formUnion(course.weeks)
            } else {
                result.append(course)
            }
        }
        return result
    }
}
