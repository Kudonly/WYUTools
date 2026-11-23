import SwiftUI

/// 体测：总分、等级与各项成绩，多个学期按时间倒序。
struct PhysicalView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = AcademicsStore.shared
    @State private var auth = AuthStore.shared
    @State private var showLogin = false

    var body: some View {
        WKPageScroll {
            if !auth.loggedIn {
                WKLoginRequiredCard(message: "登录统一身份认证后即可查看体测成绩。") {
                    showLogin = true
                }
            } else if store.physicalLoading && store.physical == nil {
                WKStatusCard(kind: .loading, message: "正在读取体测成绩…")
            } else if let error = store.physicalError {
                WKStatusCard(kind: .error, message: error) {
                    Task { await store.loadPhysical(force: true) }
                }
            } else if let tests = store.physical, !tests.isEmpty {
                ForEach(tests) { test in
                    VStack(alignment: .leading, spacing: 10) {
                        WKSectionTitle(text: test.termName)
                            .padding(.horizontal, -2)

                        GlassCard {
                            HStack(spacing: 16) {
                                WKStatTile(
                                    title: "总分",
                                    value: test.total ?? "—",
                                    tint: settings.accentColor
                                )
                                WKStatTile(title: "等级", value: test.level ?? "—", tint: .secondary)
                            }
                            if let remark = test.remark {
                                Text(remark)
                                    .font(.system(size: 12))
                                    .foregroundStyle(.secondary)
                                    .padding(.top, 6)
                            }
                        }

                        ForEach(test.groups) { group in
                            GlassCard {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(group.title)
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(settings.accentColor)
                                    ForEach(group.items) { item in
                                        HStack(alignment: .firstTextBaseline) {
                                            Text(item.name)
                                                .font(.system(size: 13))
                                                .foregroundStyle(.secondary)
                                            Spacer()
                                            if let value = item.value {
                                                Text(value)
                                                    .font(.system(size: 14, weight: .medium))
                                            }
                                            if let score = item.score {
                                                Text(score)
                                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                                    .foregroundStyle(settings.accentColor)
                                            }
                                            if let level = item.level {
                                                WKChip(text: level)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                WKStatusCard(kind: .empty, message: "还没有体测记录。")
            }
        }
        .navigationTitle("体测")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await store.loadPhysical(force: true) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(store.physicalLoading || !auth.loggedIn)
            }
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
        }
        .task {
            guard auth.loggedIn else { return }
            await store.loadPhysical()
        }
    }
}

#Preview {
    NavigationStack {
        PhysicalView()
    }
    .environment(AppSettings.shared)
}
