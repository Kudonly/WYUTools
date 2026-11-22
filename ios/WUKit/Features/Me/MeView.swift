import SwiftUI

/// 我：资料卡 + 教务账号 + 设置，排布和 Android 的「我」页一致。
struct MeView: View {
    @Environment(AppSettings.self) private var settings
    @AppStorage("profile.name") private var name = "演示同学"
    @AppStorage("profile.account") private var account = "20230001"
    @AppStorage("profile.dept") private var dept = "示例学院 · 示例专业"

    @State private var showEdit = false
    @State private var showLogin = false
    @State private var showSettings = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                profileCard
                jxglCard
                settingsCard

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
            LoginPlaceholderSheet()
        }
        .navigationDestination(isPresented: $showSettings) {
            SettingsView()
        }
    }

    // MARK: - 资料

    private var profileCard: some View {
        GlassCardButton(action: { showEdit = true }) {
            HStack(spacing: 14) {
                avatar

                if settings.demoMode {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(name)
                            .font(.system(size: 20, weight: .semibold))
                        Text(account)
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                        Text(dept)
                            .font(.system(size: 12))
                            .foregroundStyle(.tertiary)
                            .lineLimit(1)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("未登录")
                            .font(.system(size: 20, weight: .semibold))
                        Text("点按可编辑本机资料")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer(minLength: 8)

                Image(systemName: "pencil")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.tertiary)
            }
        }
    }

    private var avatar: some View {
        ZStack {
            Circle()
                .fill(settings.accentColor.opacity(0.16))
            Text(String(name.prefix(1)))
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(settings.accentColor)
        }
        .frame(width: 56, height: 56)
    }

    // MARK: - 教务

    private var jxglCard: some View {
        GlassCardButton(action: { showLogin = true }) {
            HStack(spacing: 14) {
                Image(systemName: "graduationcap")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(settings.accentColor)
                    .frame(width: 30)

                VStack(alignment: .leading, spacing: 3) {
                    Text("教务系统")
                        .font(.system(size: 16, weight: .semibold))
                    Text("登录后同步课表、成绩、考试与教评")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }

                Spacer(minLength: 8)

                Text("未登录")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(WKInk.tertiary)
            }
        }
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

/// 教务登录入口。SSO 与滑块验证属于 P1，这里先把链路讲清楚，
/// 不做假登录，也不让按钮假装成功。
struct LoginPlaceholderSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var settings

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("登录还没接上")
                                .font(.system(size: 17, weight: .semibold))
                            Text("五邑大学统一身份认证要过 AES 加密与滑块验证，这部分排在 P1。现在可以先用本地课表、待办和主题设置。")
                                .font(.system(size: 13))
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }

                    NavigationLink {
                        NetworkCheckView()
                    } label: {
                        GlassCard {
                            WKSettingRow(
                                title: "接口连通性检测",
                                summary: "先确认五个后端域在 iOS 上能否访问",
                                showsChevron: true
                            )
                        }
                    }
                    .buttonStyle(WKPressStyle())

                    Text("已经验证过的域：authserver / jxgl / ehall / 电费 / 热水")
                        .font(.system(size: 12))
                        .foregroundStyle(.tertiary)
                        .padding(.horizontal, 4)
                }
                .padding(WK.pagePadding)
            }
            .scrollIndicators(.hidden)
            .navigationTitle("教务账号")
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
