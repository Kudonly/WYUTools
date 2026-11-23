import Foundation

/// 成绩 / 绩点 / 体测 / 教评。四块同源：同一套教务会话与学期码。
enum AcademicsAPI {
    static let gradeReferer = "https://jxgl.wyu.edu.cn/new/student/xskccj/main.page"
    static let evalReferer = "https://jxgl.wyu.edu.cn/new/student/teapj/jkList.page"

    // MARK: - 拉取

    static func fetchGrades() async throws -> String {
        try await SSOClient.shared.fetchJxglRaw(
            path: "new/student/xskccj/kccjDatas",
            form: [
                ("source", "kccjlist"),
                ("page", "1"),
                ("rows", "300"),
                ("sort", "xnxqdm,kcmc"),
                ("order", "asc")
            ],
            referer: gradeReferer,
            label: "成绩"
        )
    }

    static func fetchGradeDetail(cjdm: String) async throws -> String {
        try await SSOClient.shared.fetchJxglRaw(
            path: "new/student/xskccj/getDetail?cjdm=\(cjdm)",
            referer: gradeReferer,
            label: "成绩详情"
        )
    }

    static func fetchGradeRankSheet(cjdm: String) async throws -> String {
        try await SSOClient.shared.fetchJxglRaw(
            path: "new/student/xskccj/kccjfxd.page?cjdm=\(cjdm)",
            referer: gradeReferer,
            label: "成绩分析单",
            html: true
        )
    }

    /// scope: 1=按学期 2=按学年 3=历年
    static func fetchGpa(scope: Int) async throws -> String {
        try await SSOClient.shared.fetchJxglRaw(
            path: "new/student/xskccj/xsjdDatas?ckfs=\(scope)&ckjh=",
            referer: "https://jxgl.wyu.edu.cn/new/student/xskccj/kccjjdList.page",
            label: "平均绩点"
        )
    }

    static func fetchPhysical() async throws -> String {
        try await SSOClient.shared.fetchJxglRaw(
            path: "new/student/xstccj/datas?page=1&rows=100&sort=xnxqdm&order=asc",
            referer: "https://jxgl.wyu.edu.cn/new/student/xstccj",
            label: "体测成绩"
        )
    }

    static func fetchEvalTasks(term: String) async throws -> String {
        try await SSOClient.shared.fetchJxglRaw(
            path: "new/student/teapj/pjDatas?page=1&rows=200&xnxqdm=\(term)&primarySort=%20kcrwdm%20asc",
            referer: evalReferer,
            label: "结课评价"
        )
    }

    static func fetchEvalDetail(pjdm: String) async throws -> String {
        try await SSOClient.shared.fetchJxglRaw(
            path: "new/student/teapj/viewPjData?pjdm=\(pjdm)",
            referer: evalReferer,
            label: "评价明细"
        )
    }

    // MARK: - 解析：成绩

    static func parseGrades(_ raw: String) throws -> GradesData {
        guard let root = JSON.object(raw) else {
            throw SsoError(message: "成绩数据格式异常，请稍后重试。")
        }
        var entries: [GradeEntry] = []
        for row in root.rows() {
            guard let course = row.string("kcmc") else { continue }
            let termCode = row.string("xnxqdm") ?? ""
            entries.append(
                GradeEntry(
                    cjdm: row.string("cjdm"),
                    courseName: course,
                    termCode: termCode,
                    termName: row.string("xnxqmc") ?? TermCode.displayName(termCode),
                    scoreText: row.string("zcj") ?? row.string("zcjfs"),
                    scoreValue: row.double("zcjfs") ?? row.double("zcj"),
                    point: row.double("cjjd"),
                    credit: row.double("xf"),
                    courseCode: row.string("kcbh"),
                    category: row.string("kcdlmc"),
                    studyMode: row.string("xdfsmc"),
                    scoreMode: row.string("cjfsmc"),
                    examNature: row.string("ksxzmc"),
                    department: row.string("kkbmmc"),
                    hours: row.double("zxs").map { Int($0) },
                    remark: row.string("bz")
                )
            )
        }

        let grouped = Dictionary(grouping: entries, by: \.termCode)
        let terms = grouped.keys.sorted().map { code -> GradeTerm in
            let list = (grouped[code] ?? []).sorted { lhs, rhs in
                if lhs.courseName != rhs.courseName { return lhs.courseName < rhs.courseName }
                return (lhs.examNature ?? "") < (rhs.examNature ?? "")
            }
            return GradeTerm(
                code: code,
                name: list.first?.termName ?? TermCode.displayName(code),
                entries: list
            )
        }
        return GradesData(terms: terms)
    }

    static func parseGradeDetail(detailRaw: String, rankHtml: String?) -> GradeDetail {
        let root = JSON.object(detailRaw)
        let data = (root?["data"] as? [[String: Any]]) ?? []
        var total: String?
        var parts: [GradePart] = []
        if let first = data.first {
            total = first.string("zcj")
            for index in 1...5 {
                guard let score = first.string("cj\(index)"),
                      let name = first.string("bl\(index)mc")
                else { continue }
                parts.append(GradePart(name: name, weight: first.double("bl\(index)"), score: score))
            }
        }
        return GradeDetail(total: total, parts: parts, ranks: parseRanks(rankHtml))
    }

    private static func parseRanks(_ html: String?) -> [GradeRank] {
        guard let html, !html.isEmpty else { return [] }
        var ranks: [GradeRank] = []
        let rows = html.matches(#"<tr[^>]*>([\s\S]*?)</tr>"#)
        for row in rows {
            let cells = row.matches(#"<td[^>]*>([\s\S]*?)</td>"#).map { cleanHTML($0) }
            guard cells.count >= 4 else { continue }
            ranks.append(
                GradeRank(
                    type: cells[0],
                    name: cells[1],
                    total: Int(cells[2]),
                    rank: Int(cells[3])
                )
            )
        }
        return ranks
    }

    private static func cleanHTML(_ text: String) -> String {
        text.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - 解析：绩点

    static func parseGpa(overallRaw: String, termRaw: String?) -> GpaData {
        let overallRows = parseGpaRows(overallRaw)
        let overall = overallRows.first { $0.label.contains("历年") } ?? overallRows.first
        let terms = termRaw.map(parseGpaRows) ?? []
        return GpaData(overall: overall, terms: terms)
    }

    private static func parseGpaRows(_ raw: String) -> [GpaRow] {
        guard let root = JSON.object(raw) else { return [] }
        return root.rows().compactMap { row in
            guard let label = row.string("mc"), let gpa = row.double("pjxfjd") else { return nil }
            return GpaRow(label: label, gpa: gpa)
        }
    }

    // MARK: - 解析：体测

    static func parsePhysical(_ raw: String) throws -> [PhysicalTest] {
        guard let root = JSON.object(raw) else {
            throw SsoError(message: "体测数据格式异常，请稍后重试。")
        }
        var tests: [PhysicalTest] = []
        for row in root.rows() {
            tests.append(
                PhysicalTest(
                    termName: row.string("xnxqmc") ?? "未标注学期",
                    total: row.string("zf"),
                    level: row.string("dj"),
                    remark: row.string("tips"),
                    groups: physicalGroups(row)
                )
            )
        }
        return tests.sorted { $0.termName > $1.termName }
    }

    private static func physicalGroups(_ row: [String: Any]) -> [PhysicalGroup] {
        func item(_ name: String, _ value: String?, _ score: String? = nil, _ level: String? = nil) -> PhysicalItem {
            PhysicalItem(name: name, value: value, score: score, level: level)
        }

        let body = [
            item("身高", row.string("sg").map { "\($0) 厘米" }),
            item("体重", row.string("tz").map { "\($0) 千克" }, row.string("tzpf"), row.string("tzdj"))
        ]
        let lung = [
            item("肺活量", row.string("fhl").map { cnUnit($0, "毫升") }, row.string("fhlpf"), row.string("fhldj"))
        ]
        let sprint = [
            item("50 米跑", row.string("wsm").map { "\($0) 秒" }, row.string("wsmpf"), row.string("wsmdj"))
        ]
        let longRun = [
            item(
                "1000 / 800 米跑",
                row.string("yqmorbbm").map(secondsToText),
                row.string("yqmorbbmpf"),
                row.string("yqmorbbmdj")
            )
        ]
        let sitReach = [
            item("坐位体前屈", row.string("zwtqq").map { "\($0) 厘米" }, row.string("zwtqqpf"), row.string("zwtqqdj"))
        ]
        let jump = [
            item("立定跳远", row.string("ldty").map { cnUnit($0, "厘米") }, row.string("ldtypf"), row.string("ldtydj"))
        ]
        let strength = [
            item(
                "引体向上 / 仰卧起坐",
                row.string("ytxsorywqz").map { "\(cnUnit($0, "")) 个" },
                row.string("ytxsorywqzpf"),
                row.string("ytxsorywqzdj")
            )
        ]
        let bonus = [
            item("长跑加分", row.string("yqmorbbmjf")),
            item("力量加分", row.string("ytxsorywqzjf")),
            item("附加分", row.string("fjf"))
        ]
        let vision = [
            item("左眼裸眼视力", row.string("zylysl")),
            item("右眼裸眼视力", row.string("yylysl")),
            item("左眼屈光不正", row.string("zyqgbz")),
            item("右眼屈光不正", row.string("yyqgbz")),
            item("左眼串镜", row.string("zycj")),
            item("右眼串镜", row.string("yycj"))
        ]

        let candidates = [
            PhysicalGroup(title: "身体形态", items: body),
            PhysicalGroup(title: "肺活量", items: lung),
            PhysicalGroup(title: "速度", items: sprint),
            PhysicalGroup(title: "耐力", items: longRun),
            PhysicalGroup(title: "柔韧", items: sitReach),
            PhysicalGroup(title: "爆发力", items: jump),
            PhysicalGroup(title: "力量", items: strength),
            PhysicalGroup(title: "加分项", items: bonus),
            PhysicalGroup(title: "视力", items: vision)
        ]
        return candidates.compactMap { group in
            let items = group.items.filter { item in
                !(item.value ?? "").isEmpty || !(item.score ?? "").isEmpty
            }
            return items.isEmpty ? nil : PhysicalGroup(title: group.title, items: items)
        }
    }

    private static func cnUnit(_ raw: String, _ unit: String) -> String {
        var clean = raw
        if let number = Double(raw), number == number.rounded() {
            clean = String(Int(number))
        }
        return unit.isEmpty ? clean : "\(clean) \(unit)"
    }

    private static func secondsToText(_ raw: String) -> String {
        guard let value = Double(raw) else { return raw }
        let seconds = Int(value)
        if seconds < 60 { return "\(seconds) 秒" }
        return String(format: "%d'%02d\"", seconds / 60, seconds % 60)
    }

    // MARK: - 解析：教评

    static func parseEvalTasks(_ raw: String, term: String) -> [EvalTask] {
        guard let root = JSON.object(raw) else { return [] }
        return root.rows().compactMap { row in
            guard let course = row.string("kcmc") else { return nil }
            return EvalTask(
                pjdm: row.string("pjdm"),
                courseName: course,
                teacher: row.string("teaxm"),
                session: row.string("jxhjmc"),
                closeDate: row.string("jkrq"),
                termCode: row.string("xnxqdm") ?? term,
                termName: row.string("xnxqmc") ?? TermCode.displayName(term)
            )
        }
    }

    static func parseEvalDetail(task: EvalTask, raw: String) -> EvalDetail {
        var answers: [EvalAnswer] = []
        var total = 0.0
        var scored = !evalRows(raw).isEmpty
        for row in evalRows(raw) {
            guard let question = row.string("zbmc") else { continue }
            let answer = row.string("dtjg")
            let weight = row.double("xzpf")
            answers.append(
                EvalAnswer(
                    question: question,
                    answer: answer,
                    score: row.string("xzpf"),
                    weight: row.string("zbfz"),
                    feedback: row.string("yjfk")
                )
            )
            if let weight, let answerValue = answer.flatMap(Double.init) {
                total += weight * answerValue / 100.0
            } else {
                scored = false
            }
        }
        let totalText = scored ? String(format: "%.0f", total) : nil
        return EvalDetail(task: task, total: totalText, answers: answers)
    }

    private static func evalRows(_ raw: String) -> [[String: Any]] {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("[") {
            return (JSON.array(text) as? [[String: Any]]) ?? []
        }
        guard let root = JSON.object(text) else { return [] }
        for key in ["data", "rows", "list", "records", "result"] {
            if let array = root[key] as? [[String: Any]] { return array }
            if let inner = root[key] as? [String: Any] {
                for nested in ["data", "rows", "list", "records", "result"] {
                    if let array = inner[nested] as? [[String: Any]] { return array }
                }
            }
        }
        return []
    }
}

extension String {
    /// 取所有匹配（group 默认整个匹配）。
    func matches(_ pattern: String, group: Int = 1) -> [String] {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let range = NSRange(startIndex..., in: self)
        return regex.matches(in: self, range: range).compactMap { match in
            guard group < match.numberOfRanges, let sub = Range(match.range(at: group), in: self) else {
                return nil
            }
            return String(self[sub])
        }
    }
}
