import SwiftUI

/// 我：资料卡 + 教务账号 + 设置，排布和 Android 的「我」页一致。
struct MeView: View {
    @Environment(AppSettings.self) private var settings
    @State private var auth = AuthStore.shared

    @AppStorage("profile.name") private var localName = ""
    @AppStorage("profile.account") private var localAccount = ""
    @AppStorage("profile.dept") private var localDept = ""

    @State private var showLogin = false
    @State private var showSettings = false
    @State private var showEdit = false
    @State private var confirmLogout = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                profileCard
                jxglCard
                settingsCard

                if auth.loggedIn {
                    logoutCard
                }

                Text("WUKit for iOS · \(Self.version)")
                    .font(.system(size: 12))
                    .foregroundStyle(.tertiary)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 14)

                Color.clear.frame(height: 20)
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 6)
        }
        .background(WKWallpaperBackground())
        .scrollIndicators(.hidden)
        .navigationTitle("我")
        .toolbarTitleDisplayMode(.inlineLarge)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showSettings = true
                } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 16, weight: .semibold))
                }
                .accessibilityLabel("设置")
            }
        }
        .sheet(isPresented: $showEdit) {
            ProfileEditView()
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
        }
        .navigationDestination(isPresented: $showSettings) {
            SettingsView()
        }
        .confirmationDialog("退出统一身份认证？", isPresented: $confirmLogout, titleVisibility: .visible) {
            Button("退出登录", role: .destructive) {
                Task { await auth.logout() }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("退出后课表、成绩、体育馆等需要重新登录才能查看。")
        }
    }

    // MARK: - 资料

    private var displayName: String {
        if auth.loggedIn { return auth.profile?.name ?? "同学" }
        return localName.isEmpty ? "未登录" : localName
    }

    private var displayAccount: String {
        if auth.loggedIn { return auth.profile?.account ?? "" }
        return localAccount
    }

    private var displayDept: String {
        if auth.loggedIn { return auth.profile?.dept ?? "统一身份认证已登录" }
        return localDept.isEmpty ? "点按可编辑本机资料" : localDept
    }

    private var profileCard: some View {
        GlassCardButton(action: {
            if auth.loggedIn { showEdit = false } else { showEdit = true }
        }) {
            HStack(spacing: 14) {
                avatar

                VStack(alignment: .leading, spacing: 3) {
                    Text(displayName)
                        .font(.system(size: 20, weight: .semibold))
                        .lineLimit(1)
                    if !displayAccount.isEmpty {
                        Text(displayAccount)
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                    }
                    Text(displayDept)
                        .font(.system(size: 12))
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                if !auth.loggedIn {
                    Image(systemName: "pencil")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.tertiary)
                }
            }
        }
    }

    private var avatar: some View {
        ZStack {
            Circle()
                .fill(settings.accentColor.opacity(0.16))
            Text(String(displayName.prefix(1)))
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(settings.accentColor)
        }
        .frame(width: 56, height: 56)
    }

    // MARK: - 教务

    private var jxglCard: some View {
        GlassCardButton(action: {
            if auth.loggedIn {
                Task { await auth.prepareJxgl() }
            } else {
                showLogin = true
            }
        }) {
            HStack(spacing: 14) {
                Image(systemName: "graduationcap")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(settings.accentColor)
                    .frame(width: 30)

                VStack(alignment: .leading, spacing: 3) {
                    Text("教务系统")
                        .font(.system(size: 16, weight: .semibold))
                    Text(jxglSummary)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 8)

                Text(jxglStatusText)
                    .font(.system(size: 13))
                    .foregroundStyle(auth.jxglReady ? Color.green : Color.secondary)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(WKInk.tertiary)
            }
        }
    }

    private var jxglSummary: String {
        if !auth.loggedIn { return "登录后同步课表、成绩、考试与教评" }
        if auth.jxglConnecting { return "正在建立教务会话…" }
        if let error = auth.jxglError { return error }
        return "课表、成绩、考试、教评、体测已接通"
    }

    private var jxglStatusText: String {
        if !auth.loggedIn { return "未登录" }
        if auth.jxglConnecting { return "连接中" }
        return auth.jxglReady ? "已连接" : "点按重连"
    }

    private var settingsCard: some View {
        GlassCardButton(action: { showSettings = true }) {
            HStack(spacing: 14) {
                Image(systemName: "gearshape")
                    .font(.system(size: 19, weight: .medium))
                    .foregroundStyle(.secondary)
                    .frame(width: 30)
                Text("设置")
                    .font(.system(size: 16, weight: .semibold))
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(WKInk.tertiary)
            }
        }
    }

    private var logoutCard: some View {
        GlassCardButton(action: { confirmLogout = true }) {
            HStack(spacing: 14) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(.red)
                    .frame(width: 30)
                Text("退出登录")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.red)
                Spacer(minLength: 8)
                if auth.loggingOut {
                    ProgressView().controlSize(.small)
                }
            }
        }
    }

    static var version: String {
        let short = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
        return "\(short) (\(build))"
    }
}

/// 资料编辑：名字、学号、学院专业，只存本机。
struct ProfileEditView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("profile.name") private var name = ""
    @AppStorage("profile.account") private var account = ""
    @AppStorage("profile.dept") private var dept = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("个人信息") {
                    TextField("姓名", text: $name)
                    TextField("学号", text: $account)
                        .keyboardType(.numberPad)
                    TextField("学院 / 专业", text: $dept)
                }

                Section {
                    Text("这些信息只保存在本机，不会上传。")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("编辑资料")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

#Preview {
    NavigationStack {
        MeView()
    }
    .environment(AppSettings.shared)
}
