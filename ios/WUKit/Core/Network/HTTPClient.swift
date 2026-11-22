import Foundation

/// 统一请求入口。三件事必须在这一层处理掉：
/// 1. cookie 跨子域（authserver → jxgl → ehall），URLSession 默认按 domain 严格匹配；
/// 2. UA 伪装（电费、热水只认 Android UA）；
/// 3. 明文 HTTP（电费）依赖 Info.plist 的 ATS 例外。
final class HTTPClient {
    static let shared = HTTPClient()

    private let session: URLSession
    private let cookieStorage: HTTPCookieStorage

    init(cookieStorage: HTTPCookieStorage = .shared) {
        self.cookieStorage = cookieStorage
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = cookieStorage
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 40
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        self.session = URLSession(configuration: config)
    }

    struct Response {
        let data: Data
        let status: Int
        let headers: [AnyHashable: Any]
        let url: URL?

        var text: String {
            String(data: data, encoding: .utf8) ?? ""
        }

        var isHTML: Bool {
            let lower = text.lowercased()
            return lower.contains("<html") || lower.contains("<!doctype")
        }
    }

    func send(
        _ request: URLRequest,
        followRedirects: Bool = true
    ) async throws -> Response {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        return Response(
            data: data,
            status: http.statusCode,
            headers: http.allHeaderFields,
            url: http.url
        )
    }

    func get(
        _ url: String,
        headers: [String: String] = [:],
        cookies: [HTTPCookie]? = nil
    ) async throws -> Response {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "GET"
        apply(headers: headers, cookies: cookies, to: &request)
        return try await send(request)
    }

    func postForm(
        _ url: String,
        fields: [(String, String)],
        headers: [String: String] = [:],
        cookies: [HTTPCookie]? = nil
    ) async throws -> Response {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "POST"
        request.setValue(
            "application/x-www-form-urlencoded; charset=UTF-8",
            forHTTPHeaderField: "Content-Type"
        )
        request.httpBody = fields
            .map { "\(escape($0.0))=\(escape($0.1))" }
            .joined(separator: "&")
            .data(using: .utf8)
        apply(headers: headers, cookies: cookies, to: &request)
        return try await send(request)
    }

    func postJSON(
        _ url: String,
        body: Data,
        headers: [String: String] = [:],
        cookies: [HTTPCookie]? = nil
    ) async throws -> Response {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        apply(headers: headers, cookies: cookies, to: &request)
        return try await send(request)
    }

    // MARK: - Cookies

    /// 导出某个域下的 cookie，用于手动注入另一个子域。
    func cookies(for host: String) -> [HTTPCookie] {
        cookieStorage.cookies?.filter { $0.domain.hasSuffix(host) } ?? []
    }

    func cookieHeader(for host: String) -> String? {
        let list = cookies(for: host)
        guard !list.isEmpty else { return nil }
        return list.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    /// 把 cookie 强制写进指定请求，绕过 URLSession 的 domain 匹配。
    func injectCookieHeader(for host: String, into request: inout URLRequest) {
        if let header = cookieHeader(for: host) {
            request.setValue(header, forHTTPHeaderField: "Cookie")
        }
    }

    func clearCookies() {
        cookieStorage.cookies?.forEach(cookieStorage.deleteCookie)
    }

    // MARK: - Private

    private func apply(
        headers: [String: String],
        cookies: [HTTPCookie]?,
        to request: inout URLRequest
    ) {
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        if let cookies, !cookies.isEmpty {
            let header = cookies
                .map { "\($0.name)=\($0.value)" }
                .joined(separator: "; ")
            request.setValue(header, forHTTPHeaderField: "Cookie")
        }
    }

    private func escape(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}
