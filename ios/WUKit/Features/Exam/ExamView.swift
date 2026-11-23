import SwiftUI

/// 考试：按学期拉考试安排，按日期排，最近的在前。
struct ExamView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = AcademicsStore.shared
    @State private var auth = AuthStore.shared
    @State private var showLogin = false

    var body: some View {
        WKPageScroll {
            if !auth.loggedIn {
                WKLoginRequiredCard(message: "登录统一身份认证后即可查看考试安排。") {
                    showLogin = true
                }
            } else {
                termRow

                if store.examLoading && store.exams == nil {
                    WKStatusCard(kind: .loading, message: "正在查询考试安排…")
                }
                if let error = store.examError {
                    WKStatusCard(kind: .error, message: error) {
                        Task { await store.loadExams(force: true) }
                    }
                }

                if let data = store.exams {
                    if data.entries.isEmpty {
                        WKStatusCard(kind: .empty, message: "这个学期还没有排考。")
                    } else {
                        summary(data)
                        ForEach(data.entries) { entry in
                            examCard(entry)
                        }
                    }
                }
            }
        }
        .navigationTitle("考试")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await store.loadExams(force: true) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(store.examLoading || !auth.loggedIn)
            }
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
        }
        .task {
            guard auth.loggedIn else { return }
            if store.exams == nil { await store.loadExams() }
        }
    }

    // MARK: - 学期

    private var termRow: some View {
        HStack {
            Text(store.exams?.termName ?? TermCode.displayName(store.examTerm))
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
            Spacer()
            Menu {
                ForEach(termOptions, id: \.self) { term in
                    Button(TermCode.displayName(term)) {
                        Task { await store.loadExams(term: term, force: true) }
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text("切换学期")
                    Image(systemName: "chevron.down")
                        .font(.system(size: 10, weight: .bold))
                }
                .font(.system(size: 13, weight: .medium))
            }
        }
        .padding(.horizontal, 4)
    }

    private var termOptions: [String] {
        var terms: [String] = []
        var year = Int(TermCode.current().prefix(4)) ?? 2025
        var half = 2
        for _ in 0..<8 {
            terms.append(String(format: "%d%02d", year, half))
            if half == 1 { half = 2; year -= 1 } else { half = 1 }
        }
        return terms
    }

    // MARK: - 内容

    private func summary(_ data: ExamData) -> some View {
        GlassCard {
            HStack(spacing: 16) {
                WKStatTile(title: "考试", value: "\(data.entries.count)", unit: "场")
                if let next = data.nextFrom(Date()) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("下一场")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                        Text(next.courseName)
                            .font(.system(size: 14, weight: .semibold))
                            .lineLimit(1)
                        if let date = next.date {
                            Text(Self.dateText(date) + (next.startTime.map { " \($0)" } ?? ""))
                                .font(.system(size: 12))
                                .foregroundStyle(settings.accentColor)
                        }
                    }
                }
            }
        }
    }

    private func examCard(_ entry: ExamEntry) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(entry.courseName)
                        .font(.system(size: 17, weight: .semibold))
                    if let kind = entry.kind {
                        WKChip(text: kind, tint: settings.accentColor)
                    }
                    Spacer()
                }

                HStack(spacing: 10) {
                    if let date = entry.date {
                        Text(Self.dateText(date))
                            .font(.system(size: 13, weight: .medium))
                    } else if let raw = entry.dateRaw {
                        Text(raw).font(.system(size: 13, weight: .medium))
                    }
                    if let time = entry.timeLabel {
                        Text(time)
                            .font(.system(size: 13, design: .rounded))
                    }
                    if let section = entry.sectionLabel {
                        Text(section)
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                }

                if let room = entry.room {
                    WKInfoRow(label: "考场", value: room)
                }
                if let seat = entry.seat {
                    WKInfoRow(label: "座位", value: seat)
                }
                if let invigilators = entry.invigilators {
                    WKInfoRow(label: "监考", value: invigilators)
                }
                if let method = entry.methodLabel {
                    WKInfoRow(label: "形式", value: method)
                }
                if let count = entry.candidateCount {
                    WKInfoRow(label: "考生", value: "\(count) 人")
                }
                if let remark = entry.remark {
                    Text(remark)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    static func dateText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M 月 d 日 EEE"
        return formatter.string(from: date)
    }
}

#Preview {
    NavigationStack {
        ExamView()
    }
    .environment(AppSettings.shared)
}
