import SwiftUI

/// 教评：按学期看结课评价任务与已提交的评分明细。
struct EvalView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = AcademicsStore.shared
    @State private var auth = AuthStore.shared
    @State private var showLogin = false
    @State private var selected: EvalTask?

    var body: some View {
        WKPageScroll {
            if !auth.loggedIn {
                WKLoginRequiredCard(message: "登录统一身份认证后即可查看结课评价。") {
                    showLogin = true
                }
            } else if store.evalLoading && store.evalTerms == nil {
                WKStatusCard(kind: .loading, message: "正在读取结课评价…")
            } else if let error = store.evalError {
                WKStatusCard(kind: .error, message: error) {
                    Task { await store.loadEval(force: true) }
                }
            } else if let terms = store.evalTerms, !terms.isEmpty {
                ForEach(terms) { term in
                    VStack(alignment: .leading, spacing: 10) {
                        WKSectionTitle(text: term.name)
                            .padding(.horizontal, -2)

                        ForEach(term.tasks) { task in
                            GlassCard {
                                Button {
                                    selected = task
                                } label: {
                                    HStack(alignment: .center, spacing: 12) {
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(task.courseName)
                                                .font(.system(size: 15, weight: .medium))
                                                .foregroundStyle(.primary)
                                            HStack(spacing: 6) {
                                                if let teacher = task.teacher { Text(teacher) }
                                                if let session = task.session { Text(session) }
                                                if let close = task.closeDate { Text("截止 \(close)") }
                                            }
                                            .font(.system(size: 11))
                                            .foregroundStyle(.secondary)
                                        }
                                        Spacer(minLength: 8)
                                        if task.pjdm != nil {
                                            Image(systemName: "chevron.right")
                                                .font(.system(size: 12, weight: .semibold))
                                                .foregroundStyle(WKInk.tertiary)
                                        } else {
                                            Text("未评价")
                                                .font(.system(size: 12))
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }

                Text("教务暂未开放学生端提交评教，这里只读已提交的记录。")
                    .font(.system(size: 12))
                    .foregroundStyle(.tertiary)
                    .padding(.horizontal, 4)
            } else {
                WKStatusCard(kind: .empty, message: "还没有结课评价记录。")
            }
        }
        .navigationTitle("教评")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await store.loadEval(force: true) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(store.evalLoading || !auth.loggedIn)
            }
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
        }
        .sheet(item: $selected) { task in
            EvalDetailSheet(task: task)
        }
        .task {
            guard auth.loggedIn else { return }
            await store.loadEval()
        }
    }
}

struct EvalDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var store = AcademicsStore.shared

    let task: EvalTask

    @State private var detail: EvalDetail?
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        NavigationStack {
            WKPageScroll {
                GlassCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(task.courseName)
                            .font(.system(size: 19, weight: .semibold))
                        HStack(spacing: 12) {
                            if let teacher = task.teacher { Text(teacher) }
                            if let session = task.session { Text(session) }
                            Text(task.termName)
                        }
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)

                        if let total = detail?.total {
                            HStack(alignment: .firstTextBaseline, spacing: 4) {
                                Text(total)
                                    .font(.system(size: 32, weight: .bold, design: .rounded))
                                Text("分")
                                    .font(.system(size: 13))
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.top, 4)
                        }
                    }
                }

                if loading {
                    WKStatusCard(kind: .loading, message: "正在读取评价明细…")
                } else if let error {
                    WKStatusCard(kind: .error, message: error)
                } else if let detail {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(Array(detail.answers.enumerated()), id: \.element.id) { index, answer in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("\(index + 1). \(answer.question)")
                                        .font(.system(size: 13))
                                    HStack(spacing: 8) {
                                        if let value = answer.answer {
                                            WKChip(text: "\(value)%")
                                        }
                                        if let score = answer.score {
                                            Text("分值 \(score)")
                                                .font(.system(size: 11))
                                                .foregroundStyle(.secondary)
                                        }
                                        if let weight = answer.weight {
                                            Text("权重 \(weight)")
                                                .font(.system(size: 11))
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                    }
                                    if let feedback = answer.feedback {
                                        Text(feedback)
                                            .font(.system(size: 11))
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                if index != detail.answers.count - 1 { WKDivider() }
                            }
                        }
                    }
                }
            }
            .navigationTitle("评价明细")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .task {
            guard detail == nil else { return }
            loading = true
            if task.pjdm == nil {
                error = "这门课还没有可查看的评价记录。"
                loading = false
                return
            }
            detail = await store.evalDetail(for: task)
            if detail == nil { error = "教务没有返回这门课的评价明细。" }
            loading = false
        }
    }
}

#Preview {
    NavigationStack {
        EvalView()
    }
    .environment(AppSettings.shared)
}
