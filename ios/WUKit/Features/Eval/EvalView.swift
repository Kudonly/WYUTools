import SwiftUI

/// 教评：和安卓一样分成「本学期」与「往期」两页。
/// 本学期只看当前学期；往期按学期胶囊逐学期查看。
struct EvalView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = AcademicsStore.shared
    @State private var auth = AuthStore.shared
    @State private var showLogin = false
    @State private var selected: EvalTask?
    @State private var tab = 0
    /// 往期当前选中的学期代码；空串表示还没选过。
    @State private var selectedTerm = ""

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
            } else if let terms = store.evalTerms {
                Picker("", selection: $tab) {
                    Text("本学期").tag(0)
                    Text("往期").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 2)

                if tab == 0 {
                    currentSection(terms)
                } else {
                    pastSection(terms)
                }

                Text("结课评价由教务系统发布，评价内容与得分以教务为准。教务当前未开放学生端提交评价的入口，因此这里只能查看已提交的评价明细。")
                    .font(.system(size: 11))
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

    // MARK: - 本学期

    @ViewBuilder
    private func currentSection(_ terms: [EvalTerm]) -> some View {
        let code = TermCode.current()
        if let term = terms.first(where: { $0.code == code }), !term.tasks.isEmpty {
            termHeader("本学期 · \(term.name)", tasks: term.tasks)
            ForEach(term.tasks) { task in
                taskCard(task)
            }
        } else {
            emptyCard(
                title: "暂无教评",
                body: "当前学期（\(TermCode.displayName(code))）教务还没有发布结课评价任务，发布后会显示在这里。可切到「往期」查看历史评价。"
            )
        }
    }

    // MARK: - 往期

    @ViewBuilder
    private func pastSection(_ terms: [EvalTerm]) -> some View {
        let past = terms
            .filter { $0.code != TermCode.current() }
            .sorted { $0.code > $1.code }

        if past.isEmpty {
            emptyCard(title: "暂无往期教评", body: "没有查询到往期学期的结课评价记录。")
        } else {
            let term = past.first { $0.code == selectedTerm } ?? past[0]

            termPicker(past)

            termHeader(term.name, tasks: term.tasks)
            ForEach(term.tasks) { task in
                taskCard(task)
            }
        }
    }

    /// 往期的学期胶囊，一次选一个学期。
    private func termPicker(_ terms: [EvalTerm]) -> some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(terms) { term in
                    let active = term.code == currentTermCode(terms)
                    Button {
                        withAnimation(.snappy(duration: 0.2)) { selectedTerm = term.code }
                        WKHaptics.selection()
                    } label: {
                        Text(term.name)
                            .font(.system(size: 12, weight: active ? .semibold : .medium))
                            .lineLimit(1)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                active
                                    ? AnyShapeStyle(settings.accentColor)
                                    : AnyShapeStyle(Color.primary.opacity(0.06)),
                                in: Capsule()
                            )
                            .foregroundStyle(active ? Color.white : Color.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 2)
            .padding(.vertical, 2)
        }
        .scrollIndicators(.hidden)
    }

    private func currentTermCode(_ terms: [EvalTerm]) -> String {
        terms.first { $0.code == selectedTerm }?.code ?? terms.first?.code ?? ""
    }

    // MARK: - 组件

    private func termHeader(_ title: String, tasks: [EvalTask]) -> some View {
        let done = tasks.filter { $0.pjdm != nil }.count
        return VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
            Text(
                done == tasks.count
                    ? "共 \(tasks.count) 门课程 · 全部已评价 · 点开可看明细"
                    : "共 \(tasks.count) 门课程 · 已评价 \(done) 门 · 点开可看明细"
            )
            .font(.system(size: 11))
            .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 6)
    }

    private func taskCard(_ task: EvalTask) -> some View {
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

    private func emptyCard(title: String, body: String) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                Text(body)
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
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
