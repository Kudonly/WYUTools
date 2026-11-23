import Foundation

struct GymUser {
    var name: String
    var account: String
}

struct GymSlot: Identifiable, Hashable {
    var time: String
    var startMinutes: Int
    var durationMinutes: Int?
    var id: String { time }

    var startLabel: String { time.components(separatedBy: "-").first?.trimmingCharacters(in: .whitespaces) ?? time }
    var endLabel: String { time.components(separatedBy: "-").count > 1 ? time.components(separatedBy: "-")[1].trimmingCharacters(in: .whitespaces) : "" }
}

struct GymVenue: Identifiable, Hashable {
    var wid: String
    var bizWid: String
    var name: String
    var venueType: String?
    var venueTypeId: String?
    var sportName: String?
    var sportId: String?
    var address: String?
    var openStatus: String?
    var capacity: Int
    var maxDurationMinutes: Int?
    var slots: [GymSlot]
    var id: String { wid.isEmpty ? name : wid }
}

struct GymRecord: Identifiable, Hashable {
    var wid: String
    var recordId: String?
    var venueName: String
    var slot: String
    var date: Date?
    var dateRaw: String
    var statusCode: String?
    var status: String
    var payStatusCode: String?
    var submittedAt: String?
    var durationMinutes: Int?
    var semester: String?
    var violation: Bool
    var canCancel: Bool
    var id: String { recordId ?? "\(wid)|\(dateRaw)|\(slot)" }
}

/// 体育馆预约：经 ehall 换票进入 lwWiseduCgyy，全程带统一认证 Cookie。
final class GymAPI {
    static let shared = GymAPI()

    private var pcAccessUrl: String?
    private let lock = NSLock()

    private enum Const {
        static let userAgent =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/118.0 Mobile Safari/537.36"
        static let desktopUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        static let serviceCardWid = "8558486040491173"
        static let gymServiceTypeId = "1296107574652694529"
        static let gymAppName = "体育场馆预约"
        static let gymDirectEntry = "https://ehall.wyu.edu.cn/qljfwapp/sys/lwWiseduCgyy/index.do"
    }

    func resetSession() {
        lock.lock()
        pcAccessUrl = nil
        lock.unlock()
    }

    // MARK: - 用户 / 会话

    func validateSession() async throws -> GymUser {
        let url = "https://ehall.wyu.edu.cn/getLoginUserAndGuest?_t=\(Int(Date().timeIntervalSince1970 * 1000))"
        let raw = try await execute(request(url: url, method: "GET", form: nil))
        if raw.location != nil {
            throw SsoError(message: "校园统一认证登录已失效，请重新登录。", sessionExpired: true)
        }
        guard let root = JSON.object(raw.body) else {
            throw SsoError(message: "登录信息读取失败，请重新登录。", sessionExpired: true)
        }
        try checkAuthPayload(root)
        guard let data = root["data"] as? [String: Any] else {
            throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
        }
        let account = sanitizeWyuText(JSON.text(data["userAccount"])) ?? ""
        let name = sanitizeWyuText(JSON.text(data["userName"])) ?? account
        return GymUser(name: name, account: account)
    }

    /// 建立 ehall 应用态会话：优先卡片入口，其次服务列表按 typeId 再查，最后直连。
    func ensureAppSession() async throws {
        lock.lock()
        let cached = pcAccessUrl
        lock.unlock()
        if cached != nil { return }

        var entry = try? await searchGymEntry(typeId: nil)
        if entry == nil { entry = try? await searchGymEntry(typeId: Const.gymServiceTypeId) }
        let resolved = entry ?? Const.gymDirectEntry
        try await warmUp(resolved)
        lock.lock()
        pcAccessUrl = resolved
        lock.unlock()
    }

    private func searchGymEntry(typeId: String?) async throws -> String? {
        var param: [String: Any] = ["lang": "zh_CN"]
        if let typeId { param["typeId"] = typeId }
        let payload: [String: Any] = [
            "cardId": "SYS_CARD_SERVICEBUS",
            "cardWid": Const.serviceCardWid,
            "method": "renderData",
            "param": param
        ]
        guard let body = try? JSONSerialization.data(withJSONObject: payload) else { return nil }
        let url = "https://ehall.wyu.edu.cn/execCardMethod/\(Const.serviceCardWid)/SYS_CARD_SERVICEBUS?n=\(Double.random(in: 0..<1))"
        let raw = try await execute(
            request(url: url, method: "POST", form: nil, json: body, userAgent: Const.desktopUserAgent)
        )
        if raw.location != nil {
            throw SsoError(message: "校园统一认证登录已失效，请重新登录。", sessionExpired: true)
        }
        guard let root = JSON.object(raw.body) else { return nil }
        try checkAuthPayload(root)
        guard let data = root["data"] as? [String: Any],
              let appData = data["appData"] as? [[String: Any]]
        else { return nil }
        for service in appData {
            let name = sanitizeWyuText(JSON.text(service["appName"]))
                ?? sanitizeWyuText(JSON.text(service["serviceName"]))
            if name == Const.gymAppName,
               let url = sanitizeWyuText(JSON.text(service["pcAccessUrl"])) {
                return url
            }
        }
        return nil
    }

    private func warmUp(_ entry: String) async throws {
        var url = entry
        for _ in 0..<5 {
            let raw = try await execute(request(url: url, method: "GET", form: nil))
            if !(300...399).contains(raw.code) {
                guard raw.code == 200 else {
                    throw SsoError(message: "体育馆预约系统访问失败（HTTP \(raw.code)）。")
                }
                return
            }
            guard let location = raw.location,
                  let next = URL(string: location, relativeTo: raw.url)?.absoluteURL
            else { return }
            url = next.absoluteString
        }
    }

    // MARK: - 场馆 / 记录

    func fetchVenues(date: Date) async throws -> [GymVenue] {
        try await ensureAppSession()
        let day = Self.dayString(date)
        let root = try await postJSON(
            "/modules/application/getOpeningRoom.do",
            fields: [
                ("BEGIN", day),
                ("END", day),
                ("querySetting", "[]"),
                ("pageSize", "100"),
                ("pageNumber", "1")
            ]
        )
        return extractRows(root, key: "getOpeningRoom")
            .compactMap { parseVenue($0, date: date) }
            .sorted { $0.name < $1.name }
    }

    func fetchRecords() async throws -> [GymRecord] {
        try await ensureAppSession()
        let root = try await postJSON(
            "/modules/myApplication/getMyAppointmentListData.do",
            fields: [("pageSize", "50"), ("pageNumber", "1")]
        )
        var seen = Set<String>()
        return extractRows(root, key: "getMyAppointmentListData")
            .compactMap { parseRecord($0) }
            .filter { seen.insert($0.id).inserted }
            .sorted { lhs, rhs in
                let left = lhs.date ?? .distantPast
                let right = rhs.date ?? .distantPast
                if left != right { return left > right }
                return (lhs.submittedAt ?? "") > (rhs.submittedAt ?? "")
            }
    }

    /// 完整预约链路，顺序与网页端一致。
    func book(user: GymUser, venue: GymVenue, slot: GymSlot, date: Date, phone: String) async throws -> GymRecord? {
        try await ensureAppSession()
        let form = buildApplyForm(user: user, venue: venue, slot: slot, date: date, phone: phone)

        try await checkRealtimeSlot(venue: venue, slot: slot, date: date)
        _ = try? await postRaw(
            "/modules/pay/getFill.do",
            fields: [("BIZ_WID", venue.bizWid), ("SYSJ", slot.time)]
        )

        let selfCheck = try await postJSON(
            "/modules/application/checkSelfTodayYyData.do",
            fields: [("YYRQ", Self.dayString(date))]
        )
        if !extractRows(selfCheck, key: "checkSelfTodayYyData").isEmpty {
            throw SsoError(message: "当天已存在预约记录，无法再次预约。")
        }

        let check = try await postJSON("/modules/application/checkCanApply.do", fields: form.map { ($0.0, $0.1) })
        if let code = check.string("code"), !code.isEmpty, code != "0" {
            throw SsoError(message: pickMessage(check) ?? "预约校验未通过。")
        }
        if let data = check["data"] as? [String: Any] {
            let canApply = data.string("canApply") ?? ""
            if canApply == "false" || canApply == "0" {
                let message = data.string("msg") ?? pickMessage(check) ?? "预约校验未通过。"
                throw SsoError(message: message)
            }
        }

        let formJSON = (try? JSONSerialization.data(withJSONObject: Dictionary(uniqueKeysWithValues: form)))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        let startFlow = try await postJSON(
            "/sys/emapflow/tasks/startFlow.do",
            fields: [
                ("formData", formJSON),
                ("id", "start"),
                ("sendMessage", "true"),
                ("commandType", "start"),
                ("execute", "do_start"),
                ("name", "提交"),
                ("commandEvent", "com.wisedu.emap.lwWiseduCgyy.service.Impl.ApplyCheckFlowService"),
                ("url", "/sys/emapflow/tasks/startFlow.do"),
                ("buttonType", "success"),
                ("taskId", ""),
                ("defKey", "lwWiseduCgyy.MainFlow")
            ]
        )
        if let succeed = startFlow.bool("succeed"), !succeed {
            throw SsoError(message: pickMessage(startFlow) ?? "预约提交失败。")
        }

        if let applyWid = form.first(where: { $0.0 == "WID" })?.1 {
            _ = try? await postJSON(
                "/modules/application/insertSelfToZtry.do",
                fields: [
                    ("APPLY_WID", applyWid),
                    ("USER_ID", user.account),
                    ("BY5", "1")
                ]
            )
        }

        let records: [GymRecord]
        do {
            records = try await fetchRecords()
        } catch let error as SsoError where error.sessionExpired {
            throw error
        } catch {
            return nil
        }

        let matches = records.filter { $0.venueName == venue.name && $0.slot == slot.time }
        if let confirmed = matches.first(where: { $0.statusCode == "001" }) { return confirmed }
        if let newest = matches.first {
            throw SsoError(
                message: newest.violation
                    ? "系统拒绝预约：当前可能处于违约冷冻期，请稍后再试或换一个项目。"
                    : "系统已自动取消该预约，可能受违约冷冻、次数上限或时段冲突限制。"
            )
        }
        return nil
    }

    func cancel(wid: String) async throws {
        try await ensureAppSession()
        _ = try? await postRaw("/modules/pay/qxPay.do", fields: [("WID", wid)])
        _ = try? await postJSON("/modules/myApplication/deleteZtcyYyData.do", fields: [("APPLY_WID", wid)])
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        let now = formatter.string(from: Date())
        let root = try await postJSON(
            "/modules/myApplication/T_WISEDU_CGYY_YY_SAVE.do",
            fields: [
                ("WID", wid),
                ("QXYY", "0"),
                ("QXSJ", now),
                ("SYZT", "003"),
                ("PAY_STATUS", "CG_QX")
            ]
        )
        if let code = root.string("code"), !code.isEmpty, code != "0" {
            throw SsoError(message: pickMessage(root) ?? "取消预约失败。")
        }
    }

    // MARK: - 内部

    private func checkRealtimeSlot(venue: GymVenue, slot: GymSlot, date: Date) async throws {
        guard !venue.wid.isEmpty else { return }
        guard let raw = try? await postRaw(
            "/modules/application/getSjdByRoom.do",
            fields: [("applyDate", Self.dayString(date)), ("roomId", venue.wid)]
        ) else { return }
        let available = parseStringArray(raw)
        if !available.isEmpty && !available.contains(slot.time) {
            throw SsoError(message: "该时段已被占用，请换一个时段。")
        }
    }

    private func buildApplyForm(
        user: GymUser,
        venue: GymVenue,
        slot: GymSlot,
        date: Date,
        phone: String
    ) -> [(String, String)] {
        let dateStr = Self.dayString(date)
        let bizWid = venue.bizWid.isEmpty ? venue.wid : venue.bizWid
        let timestamp = String(Int(Date().timeIntervalSince1970 * 1000))
        let rawVerification = "\(user.account)\(dateStr)\(slot.time)\(bizWid)_WISEDU_\(timestamp)"
        let encoded = (rawVerification.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")
            .replacingOccurrences(of: "+", with: "%20")
            .replacingOccurrences(of: "%7E", with: "~")
        let verification = Data(encoded.utf8).base64EncodedString()
        return [
            ("YYRXM", user.name),
            ("YYLX_DISPLAY", "个人预约"),
            ("YYLX", "001"),
            ("YYRQ", dateStr),
            ("SYSJ", slot.time),
            ("SYRS", "1"),
            ("LXDH", phone),
            ("HYSLX_DISPLAY", venue.venueType ?? ""),
            ("HYSLX", venue.venueTypeId ?? ""),
            ("GLBM_DISPLAY", venue.sportName ?? ""),
            ("GLBM", venue.sportId ?? ""),
            ("HYSMC", venue.name),
            ("HYZT", ""),
            ("HYMS", ""),
            ("BZ", ""),
            ("FJ", ""),
            ("WID", Self.randomHex(32)),
            ("XGH", user.account),
            ("BIZ_WID", bizWid),
            ("SYZT", "001"),
            ("VERIFICATION", verification),
            ("BCSQRS", "1"),
            ("USERID", user.account)
        ]
    }

    private func parseVenue(_ row: [String: Any], date: Date) -> GymVenue? {
        guard let name = pick(row, "HYSMC", "BIZ_WID_DISPLAY", "WID_DISPLAY", "NAME") else { return nil }
        let wid = pick(row, "WID") ?? ""
        let bizWid = pick(row, "BIZ_WID") ?? ""
        if wid.isEmpty && bizWid.isEmpty { return nil }

        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let target = calendar.startOfDay(for: date)
        let now = Date()
        let nowMinutes = calendar.component(.hour, from: now) * 60 + calendar.component(.minute, from: now)

        var slots: [GymSlot] = []
        for index in 1...7 {
            guard let day = row["day\(index)"] as? [String: Any] else { continue }
            guard let dayRaw = day.string("date"), let dayDate = Self.parseDate(dayRaw) else { continue }
            guard calendar.isDate(dayDate, inSameDayAs: target) else { continue }
            let times = (day.string("times") ?? "").trimmingCharacters(in: .whitespaces)
            guard !times.isEmpty else { continue }
            for piece in times.components(separatedBy: ",") {
                let time = piece.trimmingCharacters(in: .whitespaces)
                guard !time.isEmpty, let start = Self.startMinutes(time) else { continue }
                if calendar.isDate(dayDate, inSameDayAs: today) && start <= nowMinutes { continue }
                if !slots.contains(where: { $0.time == time }) {
                    slots.append(GymSlot(time: time, startMinutes: start, durationMinutes: Self.slotDuration(time)))
                }
            }
        }

        return GymVenue(
            wid: wid.isEmpty ? bizWid : wid,
            bizWid: bizWid.isEmpty ? wid : bizWid,
            name: name,
            venueType: pick(row, "HYSLX_DISPLAY"),
            venueTypeId: pick(row, "HYSLX"),
            sportName: pick(row, "GLBM_DISPLAY", "GLBMMC"),
            sportId: pick(row, "GLBM"),
            address: pick(row, "XXDZ"),
            openStatus: pick(row, "KFZT_DISPLAY", "KFZT"),
            capacity: row.int("RNRS") ?? 0,
            maxDurationMinutes: parseMinutes(row, "ZDKYYSC"),
            slots: slots.sorted { $0.startMinutes < $1.startMinutes }
        )
    }

    private func parseRecord(_ row: [String: Any]) -> GymRecord? {
        guard let name = pick(row, "HYSMC", "BIZ_WID_DISPLAY", "HYSLX_DISPLAY") else { return nil }
        let wid = pick(row, "WID") ?? ""
        let slot = pick(row, "SYSJ", "SJ", "timeRange") ?? "时段未知"
        let dateRaw = pick(row, "YYRQ", "YYSJ", "date") ?? ""
        let date = Self.parseDate(dateRaw)
        let syzt = pick(row, "SYZT")
        let payStatus = pick(row, "PAY_STATUS")
        let effective = payStatus == "CG_QX" ? "003" : syzt
        let status = statusLabel(effective) ?? pick(row, "SYZT_DISPLAY") ?? effective ?? "未知"
        let violation = pick(row, "SFWY") == "1" || pick(row, "SFWY_DISPLAY") == "是"
        let canCancel = effective == "001" && !wid.isEmpty && Self.isFuture(date: date, slot: slot)
        return GymRecord(
            wid: wid,
            recordId: pick(row, "ID_", "PROC_INST_ID_"),
            venueName: name,
            slot: slot,
            date: date,
            dateRaw: dateRaw,
            statusCode: effective,
            status: status,
            payStatusCode: payStatus,
            submittedAt: pick(row, "SQSJ"),
            durationMinutes: parseMinutes(row, "YDSC") ?? Self.slotDuration(slot),
            semester: pick(row, "XQ_DISPLAY", "XQ"),
            violation: violation,
            canCancel: canCancel
        )
    }

    private func parseMinutes(_ row: [String: Any], _ key: String) -> Int? {
        guard let value = row.double(key), value > 0 else { return nil }
        return Int(value)
    }

    private func statusLabel(_ code: String?) -> String? {
        switch code {
        case "001": return "未使用"
        case "002": return "已使用"
        case "003": return "已取消"
        default: return nil
        }
    }

    private func pick(_ row: [String: Any], _ keys: String...) -> String? {
        for key in keys {
            if let value = row.string(key), !value.isEmpty { return value }
        }
        return nil
    }

    private func pickMessage(_ row: [String: Any]) -> String? {
        for key in ["msg", "message", "error"] {
            if let value = row.string(key), !value.isEmpty { return value }
        }
        return nil
    }

    private func extractRows(_ root: [String: Any], key: String) -> [[String: Any]] {
        if let datas = root["datas"] as? [String: Any],
           let sub = datas[key] as? [String: Any],
           let rows = sub["rows"] as? [[String: Any]] {
            return rows
        }
        if let data = root["data"] as? [String: Any] {
            if let rows = data["rows"] as? [[String: Any]] { return rows }
            return [data]
        }
        return []
    }

    private func parseStringArray(_ raw: String) -> [String] {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return [] }
        if let array = JSON.array(text) {
            return array.compactMap { JSON.text($0) }
        }
        if let dict = JSON.object(text), let array = findFirstArray(dict) {
            return array.compactMap { JSON.text($0) }
        }
        return []
    }

    private func findFirstArray(_ dict: [String: Any]) -> [Any]? {
        for (_, value) in dict {
            if let array = value as? [Any] { return array }
            if let inner = value as? [String: Any], let array = findFirstArray(inner) { return array }
        }
        return nil
    }

    private func checkAuthPayload(_ root: [String: Any]) throws {
        let code = (root.string("code") ?? "").trimmingCharacters(in: .whitespaces)
        if code == "-401" || code == "401" {
            throw SsoError(message: "校园统一认证登录已失效，请重新登录。", sessionExpired: true)
        }
        if let message = pickMessage(root),
           ["尚未登录", "请先登录", "登录失效", "已在别处登录", "被迫退出"].contains(where: { message.contains($0) }) {
            throw SsoError(message: "校园统一认证登录已失效，请重新登录。", sessionExpired: true)
        }
    }

    // MARK: - HTTP 基础

    private struct Raw {
        var code: Int
        var body: String
        var url: URL
        var location: String?
    }

    private func baseRequest(url: String, method: String, userAgent: String = Const.userAgent) -> URLRequest {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = method
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json, text/plain, */*", forHTTPHeaderField: "Accept")
        lock.lock()
        let referer = pcAccessUrl
        lock.unlock()
        if let referer {
            request.setValue(referer, forHTTPHeaderField: "Referer")
            request.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        }
        if let cookie = SSOClient.shared.mergedCookieHeader(hosts: ["ehall.wyu.edu.cn", "authserver.wyu.edu.cn"]) {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
        }
        return request
    }

    private func request(
        url: String,
        method: String,
        form: [(String, String)]?,
        json: Data? = nil,
        userAgent: String = Const.userAgent
    ) -> URLRequest {
        var request = baseRequest(url: url, method: method, userAgent: userAgent)
        if let json {
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = json
        } else if let form {
            request.setValue(
                "application/x-www-form-urlencoded; charset=UTF-8",
                forHTTPHeaderField: "Content-Type"
            )
            request.httpBody = form
                .map { "\(Self.escape($0.0))=\(Self.escape($0.1))" }
                .joined(separator: "&")
                .data(using: .utf8)
        }
        return request
    }

    private func postRaw(_ path: String, fields: [(String, String)]) async throws -> String {
        let raw = try await execute(
            request(url: apiUrl(path), method: "POST", form: fields)
        )
        if raw.location != nil {
            throw SsoError(message: "校园统一认证登录已失效，请重新登录。", sessionExpired: true)
        }
        if raw.code == 401 || raw.code == 403 {
            throw SsoError(message: "校园统一认证登录已失效，请重新登录。", sessionExpired: true)
        }
        if raw.code >= 400 {
            throw SsoError(message: "体育馆接口请求失败（HTTP \(raw.code)）。")
        }
        return raw.body
    }

    private func postJSON(_ path: String, fields: [(String, String)]) async throws -> [String: Any] {
        let body = try await postRaw(path, fields: fields)
        guard let root = JSON.object(body) else {
            throw SsoError(message: "体育馆接口返回格式异常。")
        }
        try checkAuthPayload(root)
        return root
    }

    private func apiUrl(_ path: String) -> String {
        if path.hasPrefix("/sys/emapflow/") {
            return "https://ehall.wyu.edu.cn/qljfwapp" + path
        }
        return "https://ehall.wyu.edu.cn/qljfwapp/sys/lwWiseduCgyy" + path
    }

    private func execute(_ request: URLRequest) async throws -> Raw {
        let session = URLSession(configuration: {
            let config = URLSessionConfiguration.default
            config.httpCookieStorage = .shared
            config.httpShouldSetCookies = true
            config.timeoutIntervalForRequest = 20
            config.timeoutIntervalForResource = 40
            config.requestCachePolicy = .reloadIgnoringLocalCacheData
            return config
        }())
        let (data, response) = try await session.data(for: request)
        let http = response as? HTTPURLResponse
        let text = String(data: data, encoding: .utf8) ?? ""
        if looksLikeLoginHTML(text) {
            throw SsoError(message: "校园统一认证登录已失效，请重新登录。", sessionExpired: true)
        }
        return Raw(
            code: http?.statusCode ?? 0,
            body: text,
            url: http?.url ?? request.url!,
            location: http?.value(forHTTPHeaderField: "Location")
        )
    }

    private func looksLikeLoginHTML(_ body: String) -> Bool {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("<") else { return false }
        return trimmed.contains("pwdEncryptSalt")
            || trimmed.contains("统一身份认证")
            || trimmed.contains("authserver/login")
            || trimmed.contains("账号登录")
            || trimmed.contains("验证码登录")
    }

    // MARK: - 工具

    static func dayString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func parseDate(_ raw: String) -> Date? {
        let text = raw.trimmingCharacters(in: .whitespaces)
        guard text.count >= 10 else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: String(text.prefix(10)))
    }

    static func startMinutes(_ time: String) -> Int? {
        let start = time.components(separatedBy: "-").first?.trimmingCharacters(in: .whitespaces) ?? ""
        let parts = start.components(separatedBy: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]) else { return nil }
        return hour * 60 + minute
    }

    static func slotDuration(_ slot: String) -> Int? {
        guard slot.contains("-"), let start = startMinutes(slot) else { return nil }
        let endText = slot.components(separatedBy: "-")[1].trimmingCharacters(in: .whitespaces)
        let parts = endText.components(separatedBy: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]) else { return nil }
        let diff = hour * 60 + minute - start
        return (1...300).contains(diff) ? diff : nil
    }

    static func isFuture(date: Date?, slot: String) -> Bool {
        guard let date, let start = startMinutes(slot) else { return true }
        let slotStart = Calendar.current.date(byAdding: .minute, value: start, to: Calendar.current.startOfDay(for: date))
        return (slotStart ?? Date()) > Date()
    }

    static func randomHex(_ length: Int) -> String {
        let chars = Array("0123456789abcdef")
        return String((0..<length).map { _ in chars[Int.random(in: 0..<chars.count)] })
    }

    private static func escape(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}
