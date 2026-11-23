import Foundation
import Observation

/// 热水（浴室）账号与会话。token 存在 UserDefaults，密码可选存 Keychain。
@Observable
final class LxtStore {
    static let shared = LxtStore()

    private enum Key {
        static let account = "wk.lxt.account"
        static let token = "wk.lxt.token"
        static let password = "wk.lxt.password"
        static let remember = "wk.lxt.remember"
        static let machine = "wk.lxt.machine"
        static let machineId = "wk.lxt.machineId"
        static let siteId = "wk.lxt.siteId"
        static let siteFlag = "wk.lxt.siteFlag"
    }

    private let defaults = UserDefaults.standard

    var account: String {
        didSet { defaults.set(account, forKey: Key.account) }
    }
    var rememberPassword: Bool {
        didSet {
            defaults.set(rememberPassword, forKey: Key.remember)
            if !rememberPassword { Keychain.delete(Key.password) }
        }
    }

    var password: String
    private(set) var token: String

    var captchaCode = ""
    var captchaImage: Data?
    var captchaLoading = false
    var loginBusy = false
    var loginError: String?

    var hotCent: Int?
    var coldCent: Int?
    var balanceBusy = false
    var hotError: String?
    var coldError: String?

    /// 已绑定的房间与设备。
    var boundRoomName: String
    var boundSiteId: String
    var boundSiteFlag: Int
    /// 冷水钱包用设备 id 当 walletKey，所以必须单独存下来 ——
    /// `machine` 是查询出来的对象，冷启动后就没有了。
    var boundMachineId: String
    var machine: LxtMachine?

    var valveOpening = false
    var valveUsing: LxtMachine?
    var valveStatusText: String?
    var message: String?

    var loggedIn: Bool { !token.isEmpty }

    private init() {
        account = defaults.string(forKey: Key.account) ?? ""
        token = defaults.string(forKey: Key.token) ?? ""
        rememberPassword = defaults.bool(forKey: Key.remember)
        password = defaults.bool(forKey: Key.remember) ? (Keychain.load(Key.password) ?? "") : ""
        boundRoomName = defaults.string(forKey: Key.machine) ?? ""
        boundSiteId = defaults.string(forKey: Key.siteId) ?? ""
        boundSiteFlag = defaults.integer(forKey: Key.siteFlag)
        boundMachineId = defaults.string(forKey: Key.machineId) ?? ""
    }

    // MARK: - 登录

    func loadCaptcha() async {
        let user = account.trimmingCharacters(in: .whitespaces)
        guard user.count >= 6 else {
            message = "请先输入学号或手机号"
            return
        }
        guard !captchaLoading else { return }
        captchaLoading = true
        captchaCode = ""
        do {
            captchaImage = try await LxtAPI.captchaImage(account: user)
        } catch {
            message = error.localizedDescription
        }
        captchaLoading = false
    }

    func login() async {
        let user = account.trimmingCharacters(in: .whitespaces)
        guard user.count >= 6 else { message = "请输入学号或手机号"; return }
        guard !password.isEmpty else { message = "请输入密码"; return }
        guard captchaCode.count == 5 else { message = "请输入 5 位验证码"; return }
        guard !loginBusy else { return }
        loginBusy = true
        loginError = nil
        do {
            let result = try await LxtAPI.login(account: user, password: password, code: captchaCode)
            token = result.token
            defaults.set(result.token, forKey: Key.token)
            if rememberPassword {
                Keychain.save(password, for: Key.password)
            }
            password = rememberPassword ? password : ""
            captchaCode = ""
            captchaImage = nil
            message = "登录成功"
            loginBusy = false
            await refreshBalances()
        } catch {
            loginError = error.localizedDescription
            loginBusy = false
            await loadCaptcha()
        }
    }

    func logout() {
        token = ""
        defaults.removeObject(forKey: Key.token)
        hotCent = nil
        coldCent = nil
        captchaImage = nil
        valveUsing = nil
        message = nil
    }

    // MARK: - 余额

    func refreshBalances() async {
        guard loggedIn, !balanceBusy else { return }
        balanceBusy = true
        hotError = nil
        coldError = nil
        do {
            if let hot = try await LxtAPI.queryHot(token: token) {
                hotCent = Self.cents(hot["money"])
            }
        } catch {
            hotError = error.localizedDescription
            if (error as? LxtError)?.code == -44 { sessionExpired() }
        }
        // 冷水钱包的 walletKey 是**设备 id**（安卓传的是 bind.machineId），
        // 不是站点 id；用站点 id 查会一直拿不到数据。
        if !coldWalletKey.isEmpty {
            do {
                if let cold = try await LxtAPI.queryCold(token: token, walletKey: coldWalletKey) {
                    coldCent = Self.cents(cold["canUseMoney"])
                }
            } catch {
                coldError = error.localizedDescription
                if (error as? LxtError)?.code == -44 { sessionExpired() }
            }
        }
        balanceBusy = false
    }

    /// 冷水钱包查询键：优先用本次会话查到的设备，退回落盘的设备 id。
    var coldWalletKey: String {
        if let machine, !machine.machineId.isEmpty { return machine.machineId }
        return boundMachineId
    }

    var hasColdWallet: Bool { !coldWalletKey.isEmpty }

    private func sessionExpired() {
        token = ""
        defaults.removeObject(forKey: Key.token)
        loginError = "登录已失效，请重新登录。"
    }

    private static func cents(_ value: Any?) -> Int? {
        if let number = value as? NSNumber { return number.intValue }
        if let text = JSON.text(value), let double = Double(text) { return Int(double) }
        return nil
    }

    // MARK: - 绑定房间

    func areas(areaId: String) async throws -> [LxtAreaNode] {
        try await LxtAPI.lowerAreas(token: token, areaId: areaId)
    }

    func rooms(areaId: String) async throws -> [LxtAreaNode] {
        try await LxtAPI.rooms(token: token, areaId: areaId)
    }

    func machines(siteId: String, siteFlag: Int) async throws -> [LxtMachine] {
        try await LxtAPI.machines(token: token, siteId: siteId, siteFlag: siteFlag)
    }

    func statuses(siteId: String, siteFlag: Int) async throws -> [LxtMachineStatus] {
        try await LxtAPI.machineStatus(token: token, siteId: siteId, siteFlag: siteFlag)
    }

    func bind(machine: LxtMachine, roomName: String) {
        self.machine = machine
        boundRoomName = roomName.isEmpty ? machine.roomName : roomName
        boundSiteId = machine.siteId
        boundSiteFlag = machine.siteFlag
        boundMachineId = machine.machineId
        defaults.set(boundRoomName, forKey: Key.machine)
        defaults.set(boundSiteId, forKey: Key.siteId)
        defaults.set(boundSiteFlag, forKey: Key.siteFlag)
        defaults.set(boundMachineId, forKey: Key.machineId)
        message = "已绑定 \(boundRoomName)"
        // 刚绑定完就把冷水余额补上，不用等下一次刷新。
        Task { await refreshBalances() }
    }

    // MARK: - 开阀

    func openValve() async {
        guard loggedIn, let machine, !valveOpening else { return }
        valveOpening = true
        valveStatusText = nil
        do {
            _ = try await LxtAPI.openValve(token: token, machineId: machine.machineId, deviceVer: machine.deviceVer)
            valveUsing = machine
            valveStatusText = "已发送开阀指令，正在确认…"
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            if let status = try? await LxtAPI.valveStatus(
                token: token,
                machineId: machine.machineId,
                communicationVal: machine.deviceMac
            ) {
                valveStatusText = LxtAPI.isRunning(status) ? "正在出水" : "阀门未开启（状态 \(status)）"
                if !LxtAPI.isRunning(status) { valveUsing = nil }
            } else {
                valveStatusText = "指令已发送，请到设备上确认"
            }
        } catch {
            valveStatusText = error.localizedDescription
        }
        valveOpening = false
    }

    func checkValve() async {
        guard loggedIn, let machine else { return }
        do {
            if let status = try await LxtAPI.valveStatus(
                token: token,
                machineId: machine.machineId,
                communicationVal: machine.deviceMac
            ) {
                let running = LxtAPI.isRunning(status)
                valveStatusText = running ? "正在出水" : "阀门未开启（状态 \(status)）"
                valveUsing = running ? machine : nil
            } else {
                valveStatusText = "查询不到阀门状态"
            }
        } catch {
            valveStatusText = error.localizedDescription
        }
    }
}
