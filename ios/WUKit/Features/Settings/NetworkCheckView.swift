import SwiftUI

/// P0 最小验证：直接打五个域，看 iOS 能不能拿到响应。
/// 这里只看连通性，不做登录与解析；能返回 HTML / JSON / 401 / 302 都算通。
struct NetworkCheckView: View {
    @State private var rows: [CheckRow] = CheckRow.seeds
    @State private var running = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                GlassCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("把五个后端域各 GET 一次。")
                            .font(.system(size: 14))
                        Text("200/301/302/401 都说明网络可达；超时或 -1009 说明当前网络访问不到（电费必须校园网）。")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                        Button {
                            Task { await run() }
                        } label: {
                            HStack {
                                if running {
                                    ProgressView().controlSize(.small)
                                }
                                Text(running ? "检测中…" : "开始检测")
                                    .font(.system(size: 14, weight: .medium))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .foregroundStyle(.white)
                            .background(WKPalette.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .disabled(running)
                    }
                }

                ForEach(rows) { row in
                    GlassCard(padding: 14) {
                        HStack(spacing: 12) {
                            Circle()
                                .fill(row.tint)
                                .frame(width: 8, height: 8)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(row.name)
                                    .font(.system(size: 14, weight: .medium))
                                Text(row.detail)
                                    .font(.system(size: 11))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(2)
                            }
                            Spacer(minLength: 8)
                            Text(row.badge)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(row.tint)
                        }
                    }
                }
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 8)
            .padding(.bottom, 40)
        }
        .background(PageBackground())
        .navigationTitle("接口连通性")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func run() async {
        running = true
        defer { running = false }

        for index in rows.indices {
            rows[index].state = .running
            let started = Date()
            do {
                let response = try await HTTPClient.shared.get(rows[index].url)
                let elapsed = Int(Date().timeIntervalSince(started) * 1000)
                rows[index].state = .done(response.status, "\(elapsed) ms")
            } catch {
                rows[index].state = .failed(error.localizedDescription)
            }
        }
    }
}

struct CheckRow: Identifiable {
    enum State {
        case idle
        case running
        case done(Int, String)
        case failed(String)
    }

    let id = UUID()
    let name: String
    let url: String
    var state: State = .idle

    static let seeds: [CheckRow] = [
        CheckRow(name: "统一身份认证", url: WKEndpoint.auth + "/login"),
        CheckRow(name: "教务系统", url: WKEndpoint.jxgl + "/student/xsgrkb/main.page"),
        CheckRow(name: "服务大厅", url: WKEndpoint.ehall + "/login"),
        CheckRow(name: "电费（明文 HTTP）", url: WKEndpoint.elec + "/scp-api/electricity-recharge/getCurrentRemaining"),
        CheckRow(name: "热水", url: WKEndpoint.lxt + "/user/authentication/getCode")
    ]

    var tint: Color {
        switch state {
        case .idle: return .secondary
        case .running: return WKPalette.accent
        case .done: return .green
        case .failed: return .red
        }
    }

    var badge: String {
        switch state {
        case .idle: return "待测"
        case .running: return "…"
        case .done(let status, let time): return "\(status) · \(time)"
        case .failed: return "失败"
        }
    }

    var detail: String {
        switch state {
        case .idle: return url
        case .running: return "正在请求…"
        case .done: return url
        case .failed(let message): return message
        }
    }
}
