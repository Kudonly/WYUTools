import SwiftUI

struct MeView: View {
    @AppStorage("profile.name") private var name = "演示同学"
    @AppStorage("profile.account") private var account = "20230001"
    @AppStorage("profile.dept") private var dept = "示例学院 · 示例专业"
    @State private var showEdit = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                Text("我")
                    .font(.system(size: 32, weight: .bold))
                    .padding(.vertical, 4)

                profileCard
                settingsEntry

                Color.clear.frame(height: WK.bottomBarInset)
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 8)
        }
        .background(PageBackground())
        .scrollIndicators(.hidden)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showEdit) {
            ProfileEditView()
        }
    }

    private var profileCard: some View {
        GlassCardButton(action: { showEdit = true }) {
            HStack(spacing: 14) {
                Image(systemName: "person.fill")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(WKPalette.accent)
                    .frame(width: 52, height: 52)
                    .background(WKPalette.accent.opacity(0.12), in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(name)
                        .font(.system(size: 19, weight: .semibold))
                    Text(account)
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                    Text(dept)
                        .font(.system(size: 12))
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                Image(systemName: "pencil")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.tertiary)
            }
        }
    }

    private var settingsEntry: some View {
        NavigationLink {
            SettingsView()
        } label: {
            GlassCard {
                HStack(spacing: 14) {
                    Image(systemName: "gearshape")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(.secondary)
                        .frame(width: 34, height: 34)
                        .background(Color.primary.opacity(0.06), in: Circle())
                    Text("设置")
                        .font(.system(size: 16, weight: .medium))
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .buttonStyle(CardPressStyle())
    }
}

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
    }
}

#Preview {
    NavigationStack {
        MeView()
    }
}
