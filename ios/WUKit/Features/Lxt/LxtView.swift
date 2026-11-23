import SwiftUI
import UIKit

/// 洗澡热水：第三方账号登录 → 余额 → 绑定浴室设备 → 远程开阀。
struct LxtView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = LxtStore.shared
    @State private var showBind = false

    var body: some View {
        WKPageScroll {
            if store.loggedIn {
                balanceCard
                coldCard
                deviceCard
                valveCard
                logoutCard
            } else {
                loginCard
            }

            if let message = store.message {
                Text(message)
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 4)
            }

            Text("热水是校外的第三方系统，账号密码与教务不同，首次使用要单独登录。")
                .font(.system(size: 12))
                .foregroundStyle(.tertiary)
                .padding(.horizontal, 4)
        }
        .navigationTitle("洗澡")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if store.loggedIn {
                    Button {
                        Task { await store.refreshBalances() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
        .sheet(isPresented: $showBind) {
            LxtBindView()
        }
        .task {
            if store.loggedIn {
                await store.refreshBalances()
            } else if store.captchaImage == nil && store.account.count >= 6 {
                await store.loadCaptcha()
            }
        }
    }

    // MARK: - 登录

    private var loginCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("登录热水账号")
                    .font(.system(size: 17, weight: .semibold))

                textField("学号或手机号", text: Binding(
                    get: { store.account },
                    set: { store.account = $0 }
                ))
                secureField("密码", text: Binding(
                    get: { store.password },
                    set: { store.password = $0 }
                ))

                Toggle("记住密码", isOn: Binding(
                    get: { store.rememberPassword },
                    set: { store.rememberPassword = $0 }
                ))
                .font(.system(size: 14))
                .tint(settings.accentColor)

                HStack(spacing: 12) {
                    TextField("5 位验证码", text: Binding(
                        get: { store.captchaCode },
                        set: { store.captchaCode = String($0.filter(\.isNumber).prefix(5)) }
                    ))
                    .keyboardType(.numberPad)
                    .font(.system(size: 16, weight: .medium))
                    .padding(.horizontal, 12)
                    .frame(height: 44)
                    .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                    Button {
                        Task { await store.loadCaptcha() }
                    } label: {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color.primary.opacity(0.06))
                            if let data = store.captchaImage, let image = UIImage(data: data) {
                                Image(uiImage: image)
                                    .resizable()
                                    .scaledToFit()
                                    .padding(4)
                            } else if store.captchaLoading {
                                ProgressView().controlSize(.small)
                            } else {
                                Text("获取验证码")
                                    .font(.system(size: 12))
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .frame(width: 108, height: 44)
                    }
                    .buttonStyle(.plain)
                }

                if let error = store.loginError {
                    Text(error).font(.system(size: 13)).foregroundStyle(.red)
                }

                WKProminentButton(
                    title: store.loginBusy ? "登录中…" : "登录",
                    symbol: "drop.fill",
                    enabled: !store.loginBusy && store.captchaCode.count == 5
                ) {
                    Task { await store.login() }
                }
            }
        }
    }

    // MARK: - 余额

    private var balanceCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("热水余额")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                    Spacer()
                    if store.balanceBusy {
                        ProgressView().controlSize(.small)
                    }
                }
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(centsText(store.hotCent))
                        .font(.system(size: 38, weight: .bold, design: .rounded))
                    Text("元")
                        .font(.system(size: 14))
                        .foregroundStyle(.secondary)
                }

                if let error = store.hotError {
                    Text(error).font(.system(size: 12)).foregroundStyle(.red)
                }
            }
        }
    }

    private func centsText(_ cents: Int?) -> String {
        guard let cents else { return "—" }
        return String(format: "%.2f", Double(cents) / 100)
    }

    // MARK: - 冷水余额

    /// 冷水余额来自宿舍冷水表钱包，和热水余额是两个账户，所以单独一张卡。
    private var coldCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .center) {
                    Text("冷水余额")
                        .font(.system(size: 16, weight: .semibold))
                    Spacer()
                    Button {
                        showBind = true
                    } label: {
                        Text(store.hasColdWallet ? "更换宿舍" : "绑定宿舍")
                            .font(.system(size: 12, weight: .medium))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.primary.opacity(0.07), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }

                if store.hasColdWallet {
                    if !store.boundRoomName.isEmpty {
                        Text(store.boundRoomName)
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }

                    if let error = store.coldError {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundStyle(.red)
                    } else if store.balanceBusy && store.coldCent == nil {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text("正在读取冷水余额…")
                                .font(.system(size: 13))
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text(centsText(store.coldCent))
                                .font(.system(size: 30, weight: .semibold, design: .rounded))
                                .foregroundStyle(store.coldCent == nil ? Color.secondary : Color.primary)
                            Text("元")
                                .font(.system(size: 13))
                                .foregroundStyle(.secondary)
                        }
                    }
                } else {
                    Text("尚未绑定宿舍")
                        .font(.system(size: 14, weight: .medium))
                    Text("绑定后可按房间冷水表查询余额")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }

                Text("冷水余额来自宿舍冷水表钱包。")
                    .font(.system(size: 11))
                    .foregroundStyle(.tertiary)
            }
        }
    }

    // MARK: - 设备

    private var deviceCard: some View {
        GlassCardButton(action: { showBind = true }) {
            HStack(spacing: 14) {
                Image(systemName: "shower")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(settings.accentColor)
                    .frame(width: 30)
                VStack(alignment: .leading, spacing: 3) {
                    Text(store.boundRoomName.isEmpty ? "绑定浴室设备" : store.boundRoomName)
                        .font(.system(size: 16, weight: .semibold))
                    Text(store.machine == nil ? "选一次校区 → 楼栋 → 楼层 → 房间" : "设备 \(store.machine?.deviceMac ?? "")")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(WKInk.tertiary)
            }
        }
    }

    // MARK: - 阀门

    private var valveCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("远程开阀")
                    .font(.system(size: 16, weight: .semibold))

                Text(store.machine == nil
                     ? "先绑定浴室设备，才能远程开阀。"
                     : "开阀后直接用，结束记得到设备上关水。")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)

                if let status = store.valveStatusText {
                    Text(status)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(store.valveUsing != nil ? Color.green : Color.secondary)
                }

                HStack(spacing: 10) {
                    WKProminentButton(
                        title: store.valveOpening ? "发送中…" : "开阀用水",
                        symbol: "drop.fill",
                        enabled: store.machine != nil && !store.valveOpening
                    ) {
                        Task { await store.openValve() }
                    }

                    Button("刷新状态") {
                        Task { await store.checkValve() }
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .frame(width: 92, height: 50)
                    .background(Color.primary.opacity(0.07), in: Capsule())
                    .disabled(store.machine == nil)
                }
            }
        }
    }

    private var logoutCard: some View {
        GlassCardButton(action: { store.logout() }) {
            HStack(spacing: 14) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(.red)
                    .frame(width: 30)
                Text("退出热水账号")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.red)
                Spacer()
            }
        }
    }

    // MARK: - 输入

    private func textField(_ title: String, text: Binding<String>) -> some View {
        TextField(title, text: text)
            .keyboardType(.numberPad)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .font(.system(size: 16, weight: .medium))
            .padding(.horizontal, 12)
            .frame(height: 44)
            .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func secureField(_ title: String, text: Binding<String>) -> some View {
        SecureField(title, text: text)
            .font(.system(size: 16, weight: .medium))
            .padding(.horizontal, 12)
            .frame(height: 44)
            .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// 逐层选择：校区 → 楼栋 → 楼层 → 房间 → 设备。
struct LxtBindView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var store = LxtStore.shared

    @State private var path: [LxtAreaNode] = []
    @State private var items: [LxtAreaNode] = []
    @State private var editingRooms = false
    @State private var machines: [LxtMachine] = []
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        Text(pathTitle)
                            .font(.system(size: 14, weight: .semibold))
                        Spacer()
                        if loading { ProgressView().controlSize(.small) }
                    }
                }

                if let error {
                    Section {
                        Text(error).font(.system(size: 13)).foregroundStyle(.red)
                    }
                }

                if editingRooms {
                    Section("选择设备") {
                        if machines.isEmpty {
                            Text("这个房间没有可用设备。")
                                .font(.system(size: 13))
                                .foregroundStyle(.secondary)
                        }
                        ForEach(machines) { machine in
                            Button {
                                store.bind(machine: machine, roomName: path.last?.name ?? "")
                                dismiss()
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(machine.machineId)
                                            .font(.system(size: 14, weight: .medium))
                                        Text(machine.roomName.isEmpty ? (path.last?.name ?? "") : machine.roomName)
                                            .font(.system(size: 12))
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(.tertiary)
                                }
                            }
                        }
                    }
                } else {
                    Section(path.isEmpty ? "选择校区" : (path.count == 1 ? "选择楼栋" : (path.count == 2 ? "选择楼层" : "选择房间"))) {
                        ForEach(items) { node in
                            Button {
                                Task { await choose(node) }
                            } label: {
                                HStack {
                                    Text(node.name)
                                        .font(.system(size: 15))
                                        .foregroundStyle(.primary)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(.tertiary)
                                }
                            }
                        }
                        if items.isEmpty && !loading {
                            Text("这一层没有可选项。")
                                .font(.system(size: 13))
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                if !path.isEmpty {
                    Section {
                        Button("返回上一层") {
                            Task { await goBack() }
                        }
                        .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("绑定浴室设备")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .task { await loadLevel() }
    }

    private var pathTitle: String {
        if path.isEmpty { return "当前：学校" }
        return path.map(\.name).joined(separator: " / ")
    }

    private func choose(_ node: LxtAreaNode) async {
        path.append(node)
        if path.count == 4 {
            await loadMachines(room: node)
        } else {
            await loadLevel()
        }
    }

    private func goBack() async {
        if path.count > 1 {
            path.removeLast()
            editingRooms = false
            machines = []
            await loadLevel()
        } else {
            path.removeAll()
            editingRooms = false
            await loadLevel()
        }
    }

    private func loadLevel() async {
        loading = true
        error = nil
        do {
            switch path.count {
            case 0:
                items = try await store.areas(areaId: LxtAPI.schoolId)
            case 1, 2:
                items = try await store.areas(areaId: path.last!.id)
            default:
                items = try await store.rooms(areaId: path.last!.id)
            }
        } catch {
            self.error = error.localizedDescription
            items = []
        }
        loading = false
    }

    private func loadMachines(room: LxtAreaNode) async {
        loading = true
        error = nil
        machines = []
        do {
            machines = try await store.machines(siteId: room.id, siteFlag: room.siteFlag)
            if machines.count == 1, let only = machines.first {
                store.bind(machine: only, roomName: room.name)
                dismiss()
                return
            }
            editingRooms = true
        } catch {
            self.error = error.localizedDescription
            editingRooms = true
        }
        loading = false
    }
}

#Preview {
    NavigationStack {
        LxtView()
    }
    .environment(AppSettings.shared)
}
