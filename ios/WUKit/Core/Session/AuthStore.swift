import Foundation
import Observation
import Security

/// 极简 Keychain：只存密码这类不该进 UserDefaults 的东西。
enum Keychain {
    static func save(_ value: String, for key: String) {
        guard let data = value.data(using: .utf8) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.wyutools.wukit",
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(attributes as CFDictionary, nil)
    }

    static func load(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.wyutools.wukit",
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(_ key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.wyutools.wukit",
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// 统一身份认证会话。页面只读 `profile` / `loggedIn`，登录细节都收在这里。
@Observable
final class AuthStore {
    static let shared = AuthStore()

    private enum Key {
        static let profile = "wk.auth.profile"
        static let cookies = "wk.auth.cookies"
        static let account = "wk.auth.account"
        static let password = "wk.auth.password"
        static let loggedOut = "wk.auth.loggedOut"
    }

    private let defaults = UserDefaults.standard

    private(set) var profile: SsoProfile?
    private(set) var savedAccount: String
    private(set) var hasSavedPassword: Bool

    var loggingIn = false
    var autoLoggingIn = false
    var loggingOut = false
    var loginError: String?
    var notice: String?
    var loggedOutByUser = false

    var captcha: SliderChallenge?
    var captchaVerifying = false
    var captchaError: String?

    var jxglReady = false
    var jxglConnecting = false
    var jxglError: String?

    private struct PendingLogin {
        var account: String
        var password: String
        var page: LoginPageFields
        var remember: Bool
    }

    private var pending: PendingLogin?
    private var autoLoginAttempted = false

    var loggedIn: Bool { profile != nil }

    private init() {
        savedAccount = defaults.string(forKey: Key.account) ?? ""
        hasSavedPassword = (Keychain.load(Key.password) ?? "").isEmpty == false
        loggedOutByUser = defaults.bool(forKey: Key.loggedOut)
        restore()
    }

    var savedPassword: String { Keychain.load(Key.password) ?? "" }

    // MARK: - 恢复会话

    private func restore() {
        guard let raw = defaults.array(forKey: Key.cookies) as? [[String: Any]], !raw.isEmpty else {
            return
        }
        SSOClient.shared.restoreCookies(raw)
        if let data = defaults.data(forKey: Key.profile),
           let decoded = try? JSONDecoder().decode(SsoProfile.self, from: data) {
            profile = decoded
        }
        Task { await validateSession() }
    }

    /// 冷启动时确认 cookie 还有效；失效就清掉，但不清用户主动登录的记忆。
    func validateSession() async {
        guard profile != nil else { return }
        do {
            let fresh = try await SSOClient.shared.fetchProfile()
            if let fresh {
                apply(profile: fresh)
            } else {
                clearSession(keepAccount: true)
            }
        } catch {
            // 网络问题不动登录态，交给页面自己提示。
        }
    }

    // MARK: - 登录

    func login(account: String, password: String, remember: Bool) async {
        let user = account.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !user.isEmpty, !password.isEmpty else {
            loginError = "请输入学号和密码。"
            return
        }
        guard !loggingIn else { return }
        loggingIn = true
        loginError = nil
        captcha = nil
        captchaError = nil
        autoLoginAttempted = false

        do {
            let page = try await SSOClient.shared.fetchLoginPage()
            if await SSOClient.shared.checkNeedCaptcha(user) {
                await SSOClient.shared.triggerSlider()
                let challenge = try await SSOClient.shared.openSliderCaptcha()
                pending = PendingLogin(account: user, password: password, page: page, remember: remember)
                captcha = challenge
                loggingIn = false
            } else {
                try await complete(PendingLogin(account: user, password: password, page: page, remember: remember))
            }
        } catch {
            loginError = friendly(error)
            loggingIn = false
        }
    }

    func submitCaptcha(_ payload: SliderPayload) async {
        guard let challenge = captcha, let pending else { return }
        guard !captchaVerifying else { return }
        captchaVerifying = true
        captchaError = nil
        do {
            let result = try await SSOClient.shared.verifySlider(challenge, payload: payload)
            if result.passed {
                captcha = nil
                captchaVerifying = false
                try await complete(pending)
            } else {
                captchaVerifying = false
                captchaError = result.message ?? "滑块验证未通过，请重试。"
            }
        } catch {
            captchaVerifying = false
            captchaError = friendly(error)
        }
    }

    func refreshCaptcha() async {
        guard captcha != nil, !captchaVerifying else { return }
        do {
            await SSOClient.shared.triggerSlider()
            captcha = try await SSOClient.shared.openSliderCaptcha()
            captchaError = nil
        } catch {
            captchaError = friendly(error)
        }
    }

    func dismissCaptcha() {
        captcha = nil
        captchaError = nil
        captchaVerifying = false
        pending = nil
    }

    private func complete(_ pending: PendingLogin) async throws {
        let profile = try await SSOClient.shared.submitLogin(
            username: pending.account,
            password: pending.password,
            page: pending.page
        )
        self.pending = nil
        apply(profile: profile)
        savedAccount = pending.account
        defaults.set(pending.account, forKey: Key.account)
        defaults.set(false, forKey: Key.loggedOut)
        loggedOutByUser = false
        if pending.remember {
            Keychain.save(pending.password, for: Key.password)
            hasSavedPassword = true
        } else {
            Keychain.delete(Key.password)
            hasSavedPassword = false
        }
        persistCookies()
        loggingIn = false
        loginError = nil
        notice = "已登录，欢迎 \(profile.name)。"
        jxglReady = false
        Task { await prepareJxgl() }
    }

    /// 静默重登：记住账号密码时，页面被踢回认证中心不必弹登录框。
    func silentLogin() async {
        let account = savedAccount
        let password = savedPassword
        guard !account.isEmpty, !password.isEmpty, !loggingIn, !autoLoggingIn, !autoLoginAttempted else {
            return
        }
        autoLoginAttempted = true
        autoLoggingIn = true
        do {
            let page = try await SSOClient.shared.fetchLoginPage()
            try await complete(PendingLogin(account: account, password: password, page: page, remember: true))
        } catch {
            loginError = friendly(error)
        }
        autoLoggingIn = false
    }

    func logout() async {
        guard !loggingOut else { return }
        loggingOut = true
        await SSOClient.shared.logout()
        clearSession(keepAccount: true)
        loggedOutByUser = true
        defaults.set(true, forKey: Key.loggedOut)
        GymAPI.shared.resetSession()
        loggingOut = false
        notice = "已退出登录。"
    }

    private func clearSession(keepAccount: Bool) {
        profile = nil
        jxglReady = false
        jxglError = nil
        captcha = nil
        pending = nil
        defaults.removeObject(forKey: Key.profile)
        defaults.removeObject(forKey: Key.cookies)
        if !keepAccount {
            savedAccount = ""
            defaults.removeObject(forKey: Key.account)
        }
        GymAPI.shared.resetSession()
    }

    private func apply(profile: SsoProfile) {
        self.profile = profile
        if let data = try? JSONEncoder().encode(profile) {
            defaults.set(data, forKey: Key.profile)
        }
    }

    func persistCookies() {
        defaults.set(SSOClient.shared.cookieSnapshot(), forKey: Key.cookies)
    }

    // MARK: - 教务会话

    /// 提前把 jxgl 会话换好，课表 / 成绩第一次打开就不用等两次跳转。
    func prepareJxgl() async {
        guard loggedIn, !jxglConnecting else { return }
        jxglConnecting = true
        jxglError = nil
        do {
            try await SSOClient.shared.establishJxgl()
            jxglReady = true
            persistCookies()
        } catch {
            jxglReady = false
            jxglError = friendly(error)
        }
        jxglConnecting = false
    }

    /// 会话过期时统一处理：能静默重登就重登，否则清登录态。
    func recoverSession() async {
        if hasSavedPassword {
            await silentLogin()
        } else {
            clearSession(keepAccount: true)
            loginError = "登录已失效，请重新登录。"
        }
    }

    func friendly(_ error: Error) -> String {
        if let sso = error as? SsoError { return sso.message }
        if let lxt = error as? LxtError { return lxt.message }
        let ns = error as NSError
        switch ns.code {
        case NSURLErrorNotConnectedToInternet: return "网络连接失败，请检查网络后重试。"
        case NSURLErrorTimedOut: return "网络连接超时，请稍后重试。"
        case NSURLErrorCannotConnectToHost, NSURLErrorCannotFindHost: return "无法连接服务器，请稍后重试。"
        default: return error.localizedDescription
        }
    }

    /// 供各业务页复用：捕获到会话过期就先尝试恢复，再原样抛给页面显示。
    func noteSessionExpired() {
        Task { await recoverSession() }
    }
}
