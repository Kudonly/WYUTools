import Foundation
import Observation

/// 成绩 / 绩点 / 体测 / 教评 / 考试。缓存的是原始响应，重开页面先出旧数据再刷新。
@Observable
final class AcademicsStore {
    static let shared = AcademicsStore()

    private enum Key {
        static let grades = "wk.academics.grades"
        static let gpaOverall = "wk.academics.gpaOverall"
        static let gpaTerms = "wk.academics.gpaTerms"
        static let physical = "wk.academics.physical"
        static let eval = "wk.academics.eval"
        static let exam = "wk.academics.exam"
        static let examTerm = "wk.academics.examTerm"
    }

    private let defaults = UserDefaults.standard
    var auth: AuthStore { .shared }

    // 成绩
    private(set) var grades: GradesData?
    var gradesLoading = false
    var gradesRefreshing = false
    var gradesError: String?

    // 绩点
    private(set) var gpa: GpaData?
    var gpaLoading = false
    var gpaError: String?

    // 体测
    private(set) var physical: [PhysicalTest]?
    var physicalLoading = false
    var physicalError: String?

    // 教评
    private(set) var evalTerms: [EvalTerm]?
    var evalLoading = false
    var evalError: String?

    // 考试
    private(set) var exams: ExamData?
    var examLoading = false
    var examError: String?
    var examTerm: String

    private init() {
        examTerm = defaults.string(forKey: Key.examTerm) ?? TermCode.current()
        restore()
    }

    // MARK: - 成绩

    func loadGrades(force: Bool = false) async {
        guard auth.loggedIn else {
            gradesError = "登录统一身份认证后即可查看成绩。"
            return
        }
        if gradesLoading || gradesRefreshing { return }
        if grades != nil && !force { return }
        if grades == nil { gradesLoading = true } else { gradesRefreshing = true }
        gradesError = nil
        do {
            let raw = try await AcademicsAPI.fetchGrades()
            let parsed = try AcademicsAPI.parseGrades(raw)
            defaults.set(raw, forKey: Key.grades)
            grades = parsed
        } catch {
            gradesError = fail(error)
        }
        gradesLoading = false
        gradesRefreshing = false
    }

    func gradeDetail(for entry: GradeEntry) async -> GradeDetail? {
        guard let cjdm = entry.cjdm else { return nil }
        guard let raw = try? await AcademicsAPI.fetchGradeDetail(cjdm: cjdm) else { return nil }
        let ranks = try? await AcademicsAPI.fetchGradeRankSheet(cjdm: cjdm)
        return AcademicsAPI.parseGradeDetail(detailRaw: raw, rankHtml: ranks)
    }

    // MARK: - 绩点

    func loadGpa(force: Bool = false) async {
        guard auth.loggedIn else {
            gpaError = "登录统一身份认证后即可查看平均绩点。"
            return
        }
        if gpaLoading { return }
        if gpa != nil && !force { return }
        gpaLoading = true
        gpaError = nil
        do {
            async let overall = AcademicsAPI.fetchGpa(scope: 3)
            async let terms = AcademicsAPI.fetchGpa(scope: 1)
            let overallRaw = try await overall
            let termRaw = try await terms
            gpa = AcademicsAPI.parseGpa(overallRaw: overallRaw, termRaw: termRaw)
            defaults.set(overallRaw, forKey: Key.gpaOverall)
            defaults.set(termRaw, forKey: Key.gpaTerms)
        } catch {
            gpaError = fail(error)
        }
        gpaLoading = false
    }

    // MARK: - 体测

    func loadPhysical(force: Bool = false) async {
        guard auth.loggedIn else {
            physicalError = "登录统一身份认证后即可查看体测。"
            return
        }
        if physicalLoading { return }
        if physical != nil && !force { return }
        physicalLoading = true
        physicalError = nil
        do {
            let raw = try await AcademicsAPI.fetchPhysical()
            physical = try AcademicsAPI.parsePhysical(raw)
            defaults.set(raw, forKey: Key.physical)
        } catch {
            physicalError = fail(error)
        }
        physicalLoading = false
    }

    // MARK: - 教评

    func loadEval(force: Bool = false) async {
        guard auth.loggedIn else {
            evalError = "登录统一身份认证后即可查看结课评价。"
            return
        }
        if evalLoading { return }
        if evalTerms != nil && !force { return }
        evalLoading = true
        evalError = nil

        let terms = await availableTerms()
        var rawMap: [String: String] = [:]
        var parsed: [EvalTerm] = []
        for term in terms {
            guard let raw = try? await AcademicsAPI.fetchEvalTasks(term: term) else { continue }
            rawMap[term] = raw
            let tasks = AcademicsAPI.parseEvalTasks(raw, term: term)
            if !tasks.isEmpty {
                parsed.append(EvalTerm(code: term, name: TermCode.displayName(term), tasks: tasks))
            }
        }
        if parsed.isEmpty {
            evalError = evalTerms == nil ? "教务没有返回结课评价。" : nil
        } else {
            evalTerms = parsed.sorted { $0.code > $1.code }
            if let data = try? JSONSerialization.data(withJSONObject: rawMap) {
                defaults.set(data, forKey: Key.eval)
            }
        }
        evalLoading = false
    }

    func evalDetail(for task: EvalTask) async -> EvalDetail? {
        guard let pjdm = task.pjdm else { return nil }
        guard let raw = try? await AcademicsAPI.fetchEvalDetail(pjdm: pjdm) else { return nil }
        let detail = AcademicsAPI.parseEvalDetail(task: task, raw: raw)
        return detail.answers.isEmpty ? nil : detail
    }

    /// 教评可选学期：从最早有成绩的学期到当前学期。
    private func availableTerms() async -> [String] {
        let current = TermCode.current()
        var earliest = grades?.earliestTermCode
        if earliest == nil {
            if let raw = try? await AcademicsAPI.fetchGrades(),
               let parsed = try? AcademicsAPI.parseGrades(raw) {
                grades = parsed
                defaults.set(raw, forKey: Key.grades)
                earliest = parsed.earliestTermCode
            }
        }
        if let earliest {
            return TermCode.all(from: earliest, to: current)
        }
        if let account = auth.profile?.account, account.count >= 4 {
            let start = account.index(account.startIndex, offsetBy: 2)
            let end = account.index(account.startIndex, offsetBy: 4)
            if let year = Int(account[start..<end]) {
                return TermCode.all(from: String(format: "%d01", 2000 + year), to: current)
            }
        }
        return [current]
    }

    // MARK: - 考试

    func loadExams(term: String? = nil, force: Bool = false) async {
        if let term, term != examTerm {
            examTerm = term
            exams = nil
            defaults.set(term, forKey: Key.examTerm)
        }
        guard auth.loggedIn else {
            examError = "登录统一身份认证后即可查看考试安排。"
            return
        }
        if examLoading { return }
        if exams != nil && !force && exams?.termCode == examTerm { return }
        examLoading = true
        examError = nil
        do {
            let data = try await ExamAPI.fetch(term: examTerm)
            exams = data
            if let raw = try? JSONSerialization.data(withJSONObject: [
                "termCode": data.termCode,
                "termName": data.termName,
                "rows": data.entries.map { entry -> [String: Any] in
                    [
                        "course": entry.courseName,
                        "date": entry.dateRaw ?? "",
                        "start": entry.startTime ?? "",
                        "end": entry.endTime ?? "",
                        "section": entry.sectionLabel ?? "",
                        "room": entry.room ?? "",
                        "seat": entry.seat ?? "",
                        "kind": entry.kind ?? "",
                        "invigilators": entry.invigilators ?? "",
                        "form": entry.assessmentForm ?? "",
                        "mode": entry.paperMode ?? "",
                        "count": entry.candidateCount ?? "",
                        "remark": entry.remark ?? ""
                    ]
                }
            ]) {
                defaults.set(raw, forKey: Key.exam)
            }
        } catch {
            examError = fail(error)
        }
        examLoading = false
    }

    // MARK: - 内部

    /// 会话过期的错误顺手交给 AuthStore 处理，页面只负责显示文字。
    private func fail(_ error: Error) -> String {
        if let sso = error as? SsoError, sso.sessionExpired {
            auth.noteSessionExpired()
        }
        return auth.friendly(error)
    }

    private func restore() {
        if let raw = defaults.string(forKey: Key.grades),
           let parsed = try? AcademicsAPI.parseGrades(raw) {
            grades = parsed
        }
        let overall = defaults.string(forKey: Key.gpaOverall)
        let terms = defaults.string(forKey: Key.gpaTerms)
        if let overall {
            gpa = AcademicsAPI.parseGpa(overallRaw: overall, termRaw: terms)
        }
        if let raw = defaults.string(forKey: Key.physical),
           let parsed = try? AcademicsAPI.parsePhysical(raw) {
            physical = parsed
        }
        if let data = defaults.data(forKey: Key.eval),
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: String] {
            evalTerms = root.keys.sorted(by: >).compactMap { term -> EvalTerm? in
                let tasks = AcademicsAPI.parseEvalTasks(root[term] ?? "", term: term)
                return tasks.isEmpty ? nil : EvalTerm(code: term, name: TermCode.displayName(term), tasks: tasks)
            }
        }
        if let data = defaults.data(forKey: Key.exam),
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let termCode = root["termCode"] as? String {
            let rows = (root["rows"] as? [[String: Any]]) ?? []
            let entries = rows.compactMap { row -> ExamEntry? in
                guard let course = row["course"] as? String, !course.isEmpty else { return nil }
                let dateRaw = row["date"] as? String
                return ExamEntry(
                    courseName: course,
                    date: dateRaw.flatMap(GymAPI.parseDate),
                    dateRaw: dateRaw,
                    startTime: row["start"] as? String,
                    endTime: row["end"] as? String,
                    sectionLabel: row["section"] as? String,
                    room: row["room"] as? String,
                    seat: row["seat"] as? String,
                    kind: row["kind"] as? String,
                    invigilators: row["invigilators"] as? String,
                    assessmentForm: row["form"] as? String,
                    paperMode: row["mode"] as? String,
                    candidateCount: row["count"] as? String,
                    remark: row["remark"] as? String
                )
            }
            exams = ExamData(
                termCode: termCode,
                termName: (root["termName"] as? String) ?? TermCode.displayName(termCode),
                entries: entries
            )
        }
    }
}
