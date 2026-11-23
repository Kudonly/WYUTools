import Foundation
import CommonCrypto

// MARK: - 数据模型

struct SsoProfile: Codable, Equatable {
    var name: String
    var account: String
    var dept: String?
}

struct LoginPageFields {
    var salt: String
    var execution: String
    var lt: String
}

struct SliderChallenge: Equatable {
    var bigImage: Data
    var smallImage: Data
    var tagWidth: Int
    var yHeight: Int
    var safeSecure: String
}

struct SliderTrack {
    var a: Int
    var b: Int
    var c: Int
}

struct SliderPayload {
    var canvasLength: Int
    var moveLength: Int
    var tracks: [SliderTrack]
}

struct SliderVerifyResult {
    var passed: Bool
    var message: String?
}

/// 认证 / 教务层统一抛的错误，`sessionExpired` 决定上层是否清登录态。
struct SsoError: LocalizedError {
    var message: String
    var sessionExpired: Bool = false

    var errorDescription: String? { message }
}

/// 服务端未登录时会把字段返回成 JSON null，字符串化后就是 "null"。
func sanitizeWyuText(_ raw: String?) -> String? {
    guard var text = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else {
        return nil
    }
    if text.caseInsensitiveCompare("null") == .orderedSame { return nil }
    if text.caseInsensitiveCompare("undefined") == .orderedSame { return nil }
    return text
}

// MARK: - 客户端

/// 五邑大学统一身份认证 + 教务系统入口。
///
/// 流程与 Android 端一致：
/// 登录页解析 → checkNeedCaptcha（必要时滑块）→ AES-CBC 加密密码 →
/// POST 登录 → 跟随 302 → getLoginUserAndGuest 校验。
final class SSOClient {
    static let shared = SSOClient()

    private let session: URLSession
    private let cookies = HTTPCookieStorage.shared
    private let jxglLock = NSLock()
    private var establishingJxgl = false

    private init() {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = .shared
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 40
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.httpAdditionalHeaders = ["Accept-Language": "zh-CN,zh;q=0.9"]
        session = URLSession(
            configuration: config,
            delegate: NoRedirectDelegate(),
            delegateQueue: nil
        )
    }

    // MARK: 常量

    private enum Const {
        static let userAgent =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/118.0 Mobile Safari/537.36"
        static let desktopUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        static let loginURL = "https://authserver.wyu.edu.cn/authserver/login"
        static let serviceURL = "https://ehall.wyu.edu.cn/login"
        static let authHost = "authserver.wyu.edu.cn"
        static let jxglHome = "https://jxgl.wyu.edu.cn/"
        static let jxglDirectEntry = "https://jxgl.wyu.edu.cn/new/ssoLogin"
        static let jxglOrigin = "https://jxgl.wyu.edu.cn"
        static let jxglSchedulePage = "https://jxgl.wyu.edu.cn/new/student/xsgrkb/main.page"
        static let jxglScheduleData = "https://jxgl.wyu.edu.cn/new/student/xsgrkb/getCalendarWeekDatas"
        static let jxglExamPage = "https://jxgl.wyu.edu.cn/new/student/xsksrw/main.page"
        static let jxglExamData = "https://jxgl.wyu.edu.cn/new/student/xsksrw/paginateXsksrw"
        static let jxglCardWid = "017434820995445355"
        static let jxglAppName = "教务系统"
    }

    private let sessionHints = ["尚未登录", "请先登录", "登录失效", "已在别处登录", "被迫退出"]

    // MARK: - 登录页 / 滑块

    func fetchLoginPage() async throws -> LoginPageFields {
        let response = try await execute(get(loginURL()))
        guard response.code == 200, !response.body.isEmpty else {
            throw SsoError(message: "登录页访问失败（HTTP \(response.code)），请稍后重试。")
        }
        return try parseLoginPage(response.body)
    }

    /// 网络异常时按「不需要滑块」处理，和 Android 一致。
    func checkNeedCaptcha(_ username: String) async -> Bool {
        let encoded = username.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? username
        let url = "https://authserver.wyu.edu.cn/authserver/checkNeedCaptcha.htl"
            + "?username=\(encoded)&_=\(Int(Date().timeIntervalSince1970 * 1000))"
        do {
            let response = try await execute(get(url, userAgent: Const.userAgent))
            guard response.code == 200 else { return false }
            guard let root = JSON.object(response.body) else { return false }
            if let flag = root["isNeed"] as? Bool { return flag }
            if let text = root["isNeed"] as? String { return text.lowercased() == "true" }
            if let number = root["isNeed"] as? Int { return number != 0 }
            return false
        } catch {
            return false
        }
    }

    func triggerSlider() async {
        _ = try? await execute(get("https://authserver.wyu.edu.cn/authserver/common/toSliderCaptcha.htl"))
    }

    func openSliderCaptcha() async throws -> SliderChallenge {
        let url = "https://authserver.wyu.edu.cn/authserver/common/openSliderCaptcha.htl"
            + "?_=\(Int(Date().timeIntervalSince1970 * 1000))"
        let response = try await execute(get(url))
        guard response.code == 200 else {
            throw SsoError(message: "拉取滑块图片失败（HTTP \(response.code)）。")
        }
        guard let root = JSON.object(response.body) else {
            throw SsoError(message: "滑块数据格式异常，请重试。")
        }
        let big = decodeBase64(root.string("bigImage") ?? "")
        let small = decodeBase64(root.string("smallImage") ?? "")
        guard !big.isEmpty, small.count >= 16 else {
            throw SsoError(message: "滑块图片数据异常，请重试。")
        }
        let tail = small.suffix(16)
        let safeSecure = String(data: Data(tail), encoding: .ascii) ?? ""
        return SliderChallenge(
            bigImage: big,
            smallImage: small,
            tagWidth: root.int("tagWidth") ?? 0,
            yHeight: root.int("yHeight") ?? 0,
            safeSecure: safeSecure
        )
    }

    func verifySlider(_ challenge: SliderChallenge, payload: SliderPayload) async throws -> SliderVerifyResult {
        var body: [String: Any] = [
            "canvasLength": payload.canvasLength,
            "moveLength": payload.moveLength,
            "tracks": payload.tracks.map { ["a": $0.a, "b": $0.b, "c": $0.c] }
        ]
        body["canvasLength"] = payload.canvasLength
        guard let data = try? JSONSerialization.data(withJSONObject: body, options: []),
              let text = String(data: data, encoding: .utf8)
        else {
            throw SsoError(message: "滑块数据组装失败，请重试。")
        }
        let sign = SSOCrypto.encrypt(text, salt: challenge.safeSecure)
        let response = try await execute(
            post(
                "https://authserver.wyu.edu.cn/authserver/common/verifySliderCaptcha.htl",
                form: ["sign": sign],
                userAgent: Const.userAgent
            )
        )
        guard response.code == 200 else {
            throw SsoError(message: "滑块校验失败（HTTP \(response.code)）。")
        }
        guard let root = JSON.object(response.body) else {
            throw SsoError(message: "滑块校验响应异常。")
        }
        let code = root.string("errorCode") ?? ""
        let passed = code == "1"
        let message = sanitizeWyuText(root.string("msg")) ?? sanitizeWyuText(root.string("errorMsg"))
        return SliderVerifyResult(passed: passed, message: message)
    }

    // MARK: - 登录

    func submitLogin(username: String, password: String, page: LoginPageFields) async throws -> SsoProfile {
        let fields: [(String, String)] = [
            ("username", username),
            ("password", SSOCrypto.encrypt(password, salt: page.salt)),
            ("_eventId", "submit"),
            ("cllt", "userNameLogin"),
            ("dllt", "generalLogin"),
            ("lt", page.lt),
            ("execution", page.execution)
        ]
        var response = try await execute(post(loginURL(), form: fields, userAgent: Const.userAgent))

        if response.code == 200 && looksLikeKickOut(response.body) {
            response = try await handleKickOut(response.body)
        }

        guard response.code == 302 else {
            throw SsoError(message: mapLoginFailure(response.body))
        }
        let location = response.location ?? ""
        if location.contains("needCaptcha") {
            throw SsoError(message: "当前账号需要图形验证码，请稍后再试。")
        }
        try await followRedirects(from: response.url, location: location)

        guard let profile = try await fetchProfile() else {
            throw SsoError(message: "登录态校验失败，请稍后重试。")
        }
        return profile
    }

    /// 返回 nil 表示登录态已失效。
    func fetchProfile() async throws -> SsoProfile? {
        let url = "https://ehall.wyu.edu.cn/getLoginUserAndGuest?_t=\(Int(Date().timeIntervalSince1970 * 1000))"
        let response = try await execute(get(url))
        guard response.code == 200 else { return nil }
        guard let root = JSON.object(response.body) else { return nil }
        guard let data = root["data"] as? [String: Any] else { return nil }
        guard let account = sanitizeWyuText(JSON.text(data["userAccount"])) else { return nil }
        let name = sanitizeWyuText(JSON.text(data["userName"])) ?? account
        let dept = sanitizeWyuText(JSON.text(data["deptName"]))
        return SsoProfile(name: name, account: account, dept: dept)
    }

    func logout() async {
        let service = "https://ehall.wyu.edu.cn/default/index.html"
            .addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        _ = try? await execute(
            get("https://authserver.wyu.edu.cn/authserver/logout?service=\(service)")
        )
        clearCookies()
    }

    // MARK: - 教务会话

    /// 建立教务系统应用态会话（优先 ehall 卡片入口，失败回退 jxgl 自己的 CAS 入口）。
    func establishJxgl() async throws {
        jxglLock.lock()
        if establishingJxgl {
            jxglLock.unlock()
            return
        }
        establishingJxgl = true
        jxglLock.unlock()
        defer {
            jxglLock.lock()
            establishingJxgl = false
            jxglLock.unlock()
        }

        var candidates: [String] = []
        if let card = try? await fetchJxglCardEntry() { candidates.append(card) }
        candidates.append(Const.jxglDirectEntry)

        var lastError: SsoError?
        for entry in candidates {
            do {
                try await warmUpJxgl(entry)
                return
            } catch let error as SsoError {
                if error.sessionExpired { throw error }
                lastError = error
            }
        }
        throw lastError ?? SsoError(message: "教务系统访问失败，请稍后重试。")
    }

    func fetchJxglSchedule(termCode: String) async throws -> String {
        func request() async throws -> RawResponse {
            try await execute(
                post(
                    Const.jxglScheduleData,
                    form: [("xnxqdm", termCode), ("zc", "")],
                    userAgent: Const.userAgent,
                    extra: [
                        "Accept": "application/json, text/javascript, */*; q=0.01",
                        "X-Requested-With": "XMLHttpRequest",
                        "Referer": Const.jxglSchedulePage
                    ]
                )
            )
        }

        var response = try await request()
        if jxglNeedsLogin(response) {
            try await establishJxgl()
            response = try await request()
        }
        guard response.code == 200 else {
            throw SsoError(message: "课表获取失败（HTTP \(response.code)），请稍后重试。")
        }
        if jxglNeedsLogin(response) {
            throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
        }
        if response.body.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("<") {
            throw SsoError(message: "教务系统返回了异常页面，请稍后重试。")
        }
        guard let root = JSON.object(response.body) else {
            throw SsoError(message: "课表数据格式异常，请稍后重试。")
        }
        try checkAuthPayload(root)
        return response.body
    }

    func fetchJxglExams(termCode: String) async throws -> String {
        func request() async throws -> RawResponse {
            try await execute(
                post(
                    Const.jxglExamData,
                    form: [
                        ("xnxqdm", termCode),
                        ("page", "1"),
                        ("rows", "200"),
                        ("sort", "xsxm"),
                        ("order", "asc")
                    ],
                    userAgent: Const.userAgent,
                    extra: [
                        "Accept": "application/json, text/javascript, */*; q=0.01",
                        "X-Requested-With": "XMLHttpRequest",
                        "Origin": Const.jxglOrigin,
                        "Referer": Const.jxglExamPage
                    ]
                )
            )
        }

        var response = try await request()
        if jxglNeedsLogin(response) {
            try await establishJxgl()
            response = try await request()
        }
        guard response.code == 200 else {
            throw SsoError(message: "考试安排获取失败（HTTP \(response.code)），请稍后重试。")
        }
        if jxglNeedsLogin(response) {
            throw SsoError(message: "教务系统没有返回考试数据，请稍后重试。")
        }
        if response.body.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("<") {
            throw SsoError(message: "教务系统返回了异常页面，请稍后重试。")
        }
        guard let root = JSON.object(response.body) else {
            throw SsoError(message: "考试数据格式异常，请稍后重试。")
        }
        try checkAuthPayload(root)
        return response.body
    }

    /// 成绩 / 绩点 / 体测 / 教评共用的通用入口。
    func fetchJxglRaw(
        path: String,
        form: [(String, String)]? = nil,
        referer: String = Const.jxglHome,
        label: String,
        html: Bool = false
    ) async throws -> String {
        let trimmedPath = path.hasPrefix("/") ? String(path.dropFirst()) : path
        let url = Const.jxglOrigin + "/" + trimmedPath

        func request() async throws -> RawResponse {
            let headers = [
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With": "XMLHttpRequest",
                "Referer": referer
            ]
            if let form {
                return try await execute(
                    post(url, form: form, userAgent: Const.userAgent, extra: headers)
                )
            }
            return try await execute(get(url, userAgent: Const.userAgent, extra: headers))
        }

        var response = try await request()
        if jxglNeedsLogin(response) {
            try await establishJxgl()
            response = try await request()
        }
        guard response.code == 200 else {
            throw SsoError(message: "\(label) 获取失败（HTTP \(response.code)），请稍后重试。")
        }
        if jxglNeedsLogin(response) {
            throw SsoError(message: "教务系统没有返回\(label)，请稍后重试。")
        }
        let body = response.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if !html {
            if body.hasPrefix("<") {
                throw SsoError(message: "教务系统返回了异常页面，请稍后重试。")
            }
            if body.hasPrefix("[") {
                guard let rows = JSON.array(body) else {
                    throw SsoError(message: "\(label) 数据格式异常，请稍后重试。")
                }
                for row in rows {
                    if let dict = row as? [String: Any] { try checkAuthPayload(dict) }
                }
            } else {
                guard let root = JSON.object(body) else {
                    throw SsoError(message: "\(label) 数据格式异常，请稍后重试。")
                }
                try checkAuthPayload(root)
            }
        }
        return response.body
    }

    // MARK: - Cookie

    /// ehall 与认证中心合并的 Cookie 头，体育馆换票要用。
    var ssoCookieHeader: String? {
        mergedCookieHeader(hosts: ["ehall.wyu.edu.cn", "authserver.wyu.edu.cn"])
    }

    /// 只按域名匹配、忽略路径，同名 Cookie 以后出现的域为准。
    func mergedCookieHeader(hosts: [String]) -> String? {
        let all = cookies.cookies ?? []
        var merged: [String: String] = [:]
        var order: [String] = []
        for host in hosts {
            for cookie in all where domainMatches(host: host, domain: cookie.domain) {
                if merged[cookie.name] == nil { order.append(cookie.name) }
                merged[cookie.name] = "\(cookie.name)=\(cookie.value)"
            }
        }
        let parts = order.compactMap { merged[$0] }
        return parts.isEmpty ? nil : parts.joined(separator: "; ")
    }

    func cookieSnapshot() -> [[String: Any]] {
        let all = cookies.cookies ?? []
        let now = Date()
        return all.filter { $0.expiresDate == nil || $0.expiresDate! > now }.map { cookie in
            var dict: [String: Any] = [
                "name": cookie.name,
                "value": cookie.value,
                "domain": cookie.domain,
                "path": cookie.path
            ]
            if let expires = cookie.expiresDate {
                dict["expiresAt"] = Int(expires.timeIntervalSince1970 * 1000)
            }
            return dict
        }
    }

    func restoreCookies(_ snapshot: [[String: Any]]) {
        for item in snapshot {
            guard let name = item["name"] as? String,
                  let value = item["value"] as? String,
                  let domain = item["domain"] as? String, !domain.isEmpty
            else { continue }
            var properties: [HTTPCookiePropertyKey: Any] = [
                .name: name,
                .value: value,
                .domain: domain,
                .path: (item["path"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "/"
            ]
            if let millis = item["expiresAt"] as? Int {
                properties[.expires] = Date(timeIntervalSince1970: Double(millis) / 1000)
            }
            if let cookie = HTTPCookie(properties: properties) {
                cookies.setCookie(cookie)
            }
        }
    }

    func clearCookies() {
        for cookie in cookies.cookies ?? [] {
            cookies.deleteCookie(cookie)
        }
    }

    // MARK: - 内部：会话流程

    private func fetchJxglCardEntry() async throws -> String? {
        let payload: [String: Any] = [
            "cardId": "SYS_CARD_SERVICEBUS",
            "cardWid": Const.jxglCardWid,
            "method": "renderData",
            "param": ["lang": "zh_CN"]
        ]
        let url = "https://ehall.wyu.edu.cn/execCardMethod/\(Const.jxglCardWid)/SYS_CARD_SERVICEBUS?n=\(Double.random(in: 0..<1))"
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return nil }
        let response = try await execute(
            postJSON(url, data: data, userAgent: Const.desktopUserAgent, extra: ["Accept": "application/json, text/plain, */*"])
        )
        if let _ = response.location {
            throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
        }
        guard response.code == 200, let root = JSON.object(response.body) else { return nil }
        try checkAuthPayload(root)
        guard let dataDict = root["data"] as? [String: Any],
              let appData = dataDict["appData"] as? [[String: Any]]
        else { return nil }
        for service in appData {
            let name = sanitizeWyuText(JSON.text(service["appName"]))
                ?? sanitizeWyuText(JSON.text(service["serviceName"]))
            if name == Const.jxglAppName {
                let entry = sanitizeWyuText(JSON.text(service["pcAccessUrl"]))
                if let entry { return entry }
            }
        }
        return nil
    }

    private func warmUpJxgl(_ entry: String) async throws {
        var url = entry
        for _ in 0..<8 {
            let response = try await execute(get(url))
            if (300...399).contains(response.code) {
                guard let location = response.location,
                      let next = URL(string: location, relativeTo: response.url)?.absoluteURL
                else { return }
                url = next.absoluteString
                continue
            }
            guard response.code == 200 else {
                throw SsoError(message: "教务系统访问失败（HTTP \(response.code)）。")
            }
            if isAuthServer(response.url.absoluteString) || looksLikeLoginPage(response.body) {
                throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
            }
            try await verifyJxglHome()
            return
        }
        throw SsoError(message: "教务系统访问超时，请稍后重试。")
    }

    private func verifyJxglHome() async throws {
        var url = Const.jxglHome
        for _ in 0..<4 {
            let response = try await execute(get(url))
            if (300...399).contains(response.code) {
                guard let location = response.location else { return }
                if isAuthServer(location) {
                    throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
                }
                guard let next = URL(string: location, relativeTo: response.url)?.absoluteURL else { return }
                url = next.absoluteString
                continue
            }
            guard response.code == 200 else {
                throw SsoError(message: "教务系统访问失败（HTTP \(response.code)）。")
            }
            if isAuthServer(response.url.absoluteString) || looksLikeLoginPage(response.body) {
                throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
            }
            return
        }
        throw SsoError(message: "教务系统访问超时，请稍后重试。")
    }

    private func followRedirects(from base: URL, location: String) async throws {
        guard var url = URL(string: location, relativeTo: base)?.absoluteURL else { return }
        for _ in 0..<6 {
            let response = try await execute(get(url.absoluteString))
            guard (300...399).contains(response.code), let next = response.location,
                  let nextURL = URL(string: next, relativeTo: response.url)?.absoluteURL
            else { return }
            url = nextURL
        }
    }

    private func handleKickOut(_ body: String) async throws -> RawResponse {
        guard let form = body.firstMatch(#"<form[^>]*id="continue"[^>]*>[\s\S]*?</form>"#) else {
            throw SsoError(message: "检测到会话冲突，请稍后重试。")
        }
        guard let execution = form.firstMatch(#"name="execution"\s+value="([^"]+)""#, group: 1),
              let eventId = form.firstMatch(#"name="_eventId"\s+value="([^"]+)""#, group: 1)
        else {
            throw SsoError(message: "会话冲突处理失败，请稍后重试。")
        }
        let response = try await execute(
            post(loginURL(), form: [("execution", execution), ("_eventId", eventId)], userAgent: Const.userAgent)
        )
        guard response.code == 302 else {
            throw SsoError(message: "接管旧会话失败，请稍后重试。")
        }
        return response
    }

    // MARK: - 内部：判定

    private func jxglNeedsLogin(_ response: RawResponse) -> Bool {
        if response.code == 401 || response.code == 403 { return true }
        if isAuthServer(response.url.absoluteString) { return true }
        if let location = response.location,
           isAuthServer(location) || location.lowercased().contains("login") {
            return true
        }
        if let regex = try? NSRegularExpression(pattern: "\"code\"\\s*:\\s*-401"),
           regex.firstMatch(in: response.body, range: NSRange(response.body.startIndex..., in: response.body)) != nil {
            return true
        }
        return looksLikeLoginPage(response.body)
    }

    private func checkAuthPayload(_ root: [String: Any]) throws {
        let code = (JSON.text(root["code"]) ?? "").trimmingCharacters(in: .whitespaces)
        if code == "-401" || code == "401" {
            throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
        }
        let message = ["msg", "message", "error"]
            .compactMap { sanitizeWyuText(JSON.text(root[$0])) }
            .first
        if let message, sessionHints.contains(where: { message.contains($0) }) {
            throw SsoError(message: "登录已失效，请重新登录。", sessionExpired: true)
        }
    }

    private func isAuthServer(_ url: String) -> Bool {
        url.lowercased().contains(Const.authHost)
    }

    private func looksLikeLoginPage(_ body: String) -> Bool {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("<") else { return false }
        if trimmed.contains("id=\"pwdEncryptSalt\"")
            || trimmed.contains("authserver/login")
            || trimmed.contains("id=\"casLoginForm\"")
            || trimmed.contains("authserver.wyu.edu.cn/authserver") {
            return true
        }
        return trimmed.contains("id=\"login-form\"") && trimmed.contains("/new/ssoLogin")
    }

    private func looksLikeKickOut(_ body: String) -> Bool {
        body.contains("kick-out") || body.contains("踢出会话")
    }

    private func parseLoginPage(_ html: String) throws -> LoginPageFields {
        let salt = extractInputValue(html, "pwdEncryptSalt")
        let execution = extractInputValue(html, "execution")
        guard let execution, let salt, !salt.isEmpty else {
            throw SsoError(message: "统一认证登录页结构已变化，请稍后重试。")
        }
        return LoginPageFields(salt: salt, execution: execution, lt: extractInputValue(html, "lt") ?? "")
    }

    private func extractInputValue(_ html: String, _ id: String) -> String? {
        let tag = html.firstMatch("<input[^>]*\\bid=\"\(id)\"[^>]*>", options: [.caseInsensitive])
            ?? html.firstMatch("<input[^>]*\\bname=\"\(id)\"[^>]*>", options: [.caseInsensitive])
        guard let tag else { return nil }
        return tag.firstMatch(#"\bvalue="([^"]*)""#, group: 1)
    }

    private func mapLoginFailure(_ body: String) -> String {
        if body.contains("您提供的用户名或者密码有误") { return "学号或密码错误。" }
        if body.contains("验证码") { return "当前账号需要验证码登录，请稍后再试。" }
        if let message = body.firstMatch(#"<span\s+id="showErrorTip"[^>]*>([^<]*)</span>"#, group: 1),
           !message.isEmpty {
            return message.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return "登录失败，请检查学号密码或稍后重试。"
    }

    private func decodeBase64(_ raw: String) -> Data {
        let cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return Data() }
        return Data(base64Encoded: cleaned, options: .ignoreUnknownCharacters) ?? Data()
    }

    private func loginURL() -> String {
        let service = Const.serviceURL.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        return "\(Const.loginURL)?service=\(service)"
    }

    // MARK: - 内部：HTTP

    struct RawResponse {
        var code: Int
        var body: String
        var url: URL
        var location: String?
    }

    private func get(
        _ url: String,
        userAgent: String = Const.userAgent,
        extra: [String: String] = [:]
    ) -> URLRequest {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "GET"
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        for (key, value) in extra { request.setValue(value, forHTTPHeaderField: key) }
        return request
    }

    private func post(
        _ url: String,
        form: [(String, String)],
        userAgent: String,
        extra: [String: String] = [:]
    ) -> URLRequest {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "POST"
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(
            "application/x-www-form-urlencoded; charset=UTF-8",
            forHTTPHeaderField: "Content-Type"
        )
        for (key, value) in extra { request.setValue(value, forHTTPHeaderField: key) }
        request.httpBody = form
            .map { "\(Self.escape($0.0))=\(Self.escape($0.1))" }
            .joined(separator: "&")
            .data(using: .utf8)
        return request
    }

    private func postJSON(
        _ url: String,
        data: Data,
        userAgent: String,
        extra: [String: String] = [:]
    ) -> URLRequest {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "POST"
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        for (key, value) in extra { request.setValue(value, forHTTPHeaderField: key) }
        request.httpBody = data
        return request
    }

    private func execute(_ request: URLRequest) async throws -> RawResponse {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        return RawResponse(
            code: http.statusCode,
            body: String(data: data, encoding: .utf8) ?? "",
            url: http.url ?? request.url!,
            location: http.value(forHTTPHeaderField: "Location")
        )
    }

    private static func escape(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private func domainMatches(host: String, domain: String) -> Bool {
        let normalized = domain.hasPrefix(".") ? String(domain.dropFirst()) : domain
        return host == normalized || host.hasSuffix(".\(normalized)")
    }
}

// MARK: - 重定向拦截

/// 登录要看到 302 + Location，所以整条链路自己跟跳转。
private final class NoRedirectDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest
    ) async -> URLRequest? {
        nil
    }
}

// MARK: - AES-CBC

enum SSOCrypto {
    private static let aasChars = Array("ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678")

    static func encrypt(_ plain: String, salt: String) -> String {
        guard !salt.isEmpty else { return plain }
        let prefixed = randomString(64) + plain
        let iv = randomString(16)
        var keyBytes = Array(salt.utf8)
        if keyBytes.count < 16 {
            keyBytes.append(contentsOf: Array(repeating: 0, count: 16 - keyBytes.count))
        } else if keyBytes.count > 16 && keyBytes.count < 24 {
            keyBytes = Array(keyBytes.prefix(16))
        } else if keyBytes.count > 24 && keyBytes.count < 32 {
            keyBytes = Array(keyBytes.prefix(24))
        } else if keyBytes.count > 32 {
            keyBytes = Array(keyBytes.prefix(32))
        }

        let input = Array(prefixed.utf8)
        let bufferSize = input.count + kCCBlockSizeAES128
        var buffer = Data(count: bufferSize)
        var moved = 0

        let status = buffer.withUnsafeMutableBytes { outBytes -> CCCryptorStatus in
            guard let out = outBytes.baseAddress else { return CCCryptorStatus(kCCMemoryFailure) }
            return iv.withUnsafeBytes { ivBytes -> CCCryptorStatus in
                guard let ivPtr = ivBytes.baseAddress else { return CCCryptorStatus(kCCMemoryFailure) }
                return keyBytes.withUnsafeBytes { keyPtr -> CCCryptorStatus in
                    guard let key = keyPtr.baseAddress else { return CCCryptorStatus(kCCMemoryFailure) }
                    return input.withUnsafeBytes { inBytes -> CCCryptorStatus in
                        guard let inputPtr = inBytes.baseAddress else {
                            return CCCryptorStatus(kCCMemoryFailure)
                        }
                        return CCCrypt(
                            UInt32(kCCEncrypt),
                            UInt32(kCCAlgorithmAES),
                            UInt32(kCCOptionPKCS7Padding),
                            key,
                            keyBytes.count,
                            ivPtr,
                            inputPtr,
                            input.count,
                            out,
                            bufferSize,
                            &moved
                        )
                    }
                }
            }
        }

        guard status == kCCSuccess else { return plain }
        return buffer.prefix(moved).base64EncodedString()
    }

    private static func randomString(_ length: Int) -> String {
        var result = ""
        result.reserveCapacity(length)
        for _ in 0..<length {
            result.append(aasChars[Int.random(in: 0..<aasChars.count)])
        }
        return result
    }
}

// MARK: - JSON 兜底

enum JSON {
    static func object(_ text: String) -> [String: Any]? {
        guard let data = text.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    static func array(_ text: String) -> [Any]? {
        guard let data = text.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [Any]
    }

    /// 教务把空值写成 "null" / "undefined" / "-"，这里统一当空。
    static func text(_ value: Any?) -> String? {
        guard let value else { return nil }
        if value is NSNull { return nil }
        let text: String
        if let string = value as? String {
            text = string
        } else if let number = value as? NSNumber {
            if CFGetTypeID(number) == CFBooleanGetTypeID() {
                text = number.boolValue ? "true" : "false"
            } else {
                let double = number.doubleValue
                text = double == double.rounded() && abs(double) < 1e15
                    ? String(Int64(double))
                    : String(double)
            }
        } else {
            text = String(describing: value)
        }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "null" || trimmed == "undefined" || trimmed == "-" {
            return nil
        }
        return trimmed
    }
}

extension String {
    /// 取第一个匹配，`group` 为 1 时返回捕获组。
    func firstMatch(_ pattern: String, options: NSRegularExpression.Options = [], group: Int = 0) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: options) else { return nil }
        let range = NSRange(startIndex..., in: self)
        guard let match = regex.firstMatch(in: self, range: range), group < match.numberOfRanges else {
            return nil
        }
        guard let sub = Range(match.range(at: group), in: self) else { return nil }
        return String(self[sub])
    }
}

extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? {
        JSON.text(self[key])
    }

    func int(_ key: String) -> Int? {
        guard let text = JSON.text(self[key]) else { return nil }
        return Int(text) ?? Int(Double(text) ?? 0)
    }

    func double(_ key: String) -> Double? {
        guard let text = JSON.text(self[key]) else { return nil }
        return Double(text)
    }

    func bool(_ key: String) -> Bool? {
        guard let value = self[key] else { return nil }
        if let flag = value as? Bool { return flag }
        if let text = JSON.text(value) {
            return text.lowercased() == "true" || text == "1"
        }
        return nil
    }

    func rows() -> [[String: Any]] {
        for key in ["rows", "data", "list", "records"] {
            if let array = self[key] as? [[String: Any]] { return array }
        }
        if let data = self["data"] as? [String: Any] {
            for key in ["rows", "list", "records"] {
                if let array = data[key] as? [[String: Any]] { return array }
            }
        }
        return []
    }
}
