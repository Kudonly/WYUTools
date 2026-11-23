import SwiftUI

/// 成绩与绩点。成绩按学期分组，点开单科能看到分项与排名。
struct GradesView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = AcademicsStore.shared
    @State private var auth = AuthStore.shared
    @State private var showLogin = false
    @State private var tab = 0
    @State private var selected: GradeEntry?
    /// 当前查看的学期代码；空串表示还没选过，默认取最新学期。
    @State private var selectedTerm = ""

    var body: some View {
        WKPageScroll {
            if !auth.loggedIn {
                WKLoginRequiredCard(message: "登录统一身份认证后即可查看成绩与绩点。") {
                    showLogin = true
                }
            } else {
                Picker("", selection: $tab) {
                    Text("成绩").tag(0)
                    Text("绩点").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 2)

                if tab == 0 {
                    gradesSection
                } else {
                    gpaSection
                }
            }
        }
        .navigationTitle("成绩")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task {
                        if tab == 0 {
                            await store.loadGrades(force: true)
                        } else {
                            await store.loadGpa(force: true)
                        }
                    }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(!auth.loggedIn)
            }
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
        }
        .sheet(item: $selected) { entry in
            GradeDetailSheet(entry: entry)
        }
        .task {
            guard auth.loggedIn else { return }
            await store.loadGrades()
        }
        .onChange(of: store.grades?.terms.first?.code) { _, code in
            guard let code, selectedTerm.isEmpty else { return }
            selectedTerm = code
        }
        .onChange(of: tab) { _, value in
            guard auth.loggedIn else { return }
            if value == 1 { Task { await store.loadGpa() } }
        }
    }

    // MARK: - 成绩

    @ViewBuilder
    private var gradesSection: some View {
        if store.gradesLoading && store.grades == nil {
            WKStatusCard(kind: .loading, message: "正在读取成绩…")
        }
        if let error = store.gradesError {
            WKStatusCard(kind: .error, message: error) {
                Task { await store.loadGrades(force: true) }
            }
        }

        if let data = store.grades {
            if data.terms.isEmpty {
                WKStatusCard(kind: .empty, message: "教务还没有返回成绩。")
            } else {
                // 最新学期在前，默认就停在这一学期。
                let terms = Array(data.terms.reversed())
                let term = terms.first { $0.code == selectedTerm } ?? terms[0]

                GlassCard {
                    HStack(spacing: 16) {
                        WKStatTile(title: "平均绩点", value: gpaText(data.overallGpa), tint: settings.accentColor)
                        WKStatTile(title: "已修学分", value: creditText(data.totalCredit), unit: "分")
                        WKStatTile(title: "课程", value: "\(data.all.count)", unit: "门", tint: .secondary)
                    }
                }

                termPicker(terms)

                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        WKSectionTitle(text: term.name)
                            .padding(.horizontal, -2)
                        Spacer()
                        Text("\(creditText(term.totalCredit)) 学分 · \(gpaText(term.gpa))")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }

                    GlassCard(padding: 0) {
                        VStack(spacing: 0) {
                            ForEach(Array(term.entries.enumerated()), id: \.element.id) { index, entry in
                                Button {
                                    selected = entry
                                } label: {
                                    gradeRow(entry)
                                }
                                .buttonStyle(.plain)

                                if index != term.entries.count - 1 {
                                    WKDivider().padding(.leading, 18)
                                }
                            }
                        }
                    }
                }

                Text("共 \(data.terms.count) 个学期 · \(data.all.count) 门课程")
                    .font(.system(size: 11))
                    .foregroundStyle(.tertiary)
                    .padding(.horizontal, 4)
            }
        }
    }

    /// 学期横排胶囊，和安卓的 TermChips 一致：一次选一个学期。
    private func termPicker(_ terms: [GradeTerm]) -> some View {
        // 数据刚到、selectedTerm 还没落值时，也把最新学期点亮。
        let effective = selectedTerm.isEmpty ? terms.first?.code : selectedTerm
        return ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(terms) { term in
                    let active = term.code == (effective ?? "")
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

    private func gradeRow(_ entry: GradeEntry) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(entry.courseName)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.primary)
                HStack(spacing: 6) {
                    if let credit = entry.credit {
                        Text(creditText(credit) + " 学分")
                    }
                    if let nature = entry.examNature {
                        Text(nature)
                    }
                    if let mode = entry.studyMode {
                        Text(mode)
                    }
                }
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 2) {
                if let passed = entry.passed {
                    Text(entry.displayScore)
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundStyle(passed ? Color.primary : Color.red)
                } else {
                    Text(entry.displayScore)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
                if let point = entry.point {
                    Text("绩点 \(String(format: "%.2f", point))")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }
            }

            if entry.cjdm != nil {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(WKInk.tertiary)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    // MARK: - 绩点

    @ViewBuilder
    private var gpaSection: some View {
        if store.gpaLoading && store.gpa == nil {
            WKStatusCard(kind: .loading, message: "正在读取平均绩点…")
        }
        if let error = store.gpaError {
            WKStatusCard(kind: .error, message: error) {
                Task { await store.loadGpa(force: true) }
            }
        }

        if let data = store.gpa {
            GlassCard {
                VStack(alignment: .leading, spacing: 6) {
                    Text(data.overall?.label ?? "平均绩点")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                    Text(data.overall.map { String(format: "%.2f", $0.gpa) } ?? "—")
                        .font(.system(size: 44, weight: .bold, design: .rounded))
                        .foregroundStyle(settings.accentColor)
                }
            }

            if !data.terms.isEmpty {
                GlassCard {
                    VStack(spacing: 10) {
                        ForEach(data.terms) { row in
                            HStack {
                                Text(row.label)
                                    .font(.system(size: 14))
                                Spacer()
                                if data.best?.label == row.label {
                                    WKChip(text: "最高", tint: .green)
                                }
                                Text(String(format: "%.2f", row.gpa))
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                            }
                        }
                    }
                }
            } else {
                WKStatusCard(kind: .empty, message: "教务没有返回按学期的绩点，只有总平均。")
            }
        }
    }

    private func gpaText(_ value: Double?) -> String {
        guard let value else { return "—" }
        return String(format: "%.2f", value)
    }

    private func creditText(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(format: "%.1f", value)
    }
}

/// 单科成绩分析单：总成绩、分项、排名。
struct GradeDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var store = AcademicsStore.shared

    let entry: GradeEntry

    @State private var detail: GradeDetail?
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        NavigationStack {
            WKPageScroll {
                GlassCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(entry.courseName)
                            .font(.system(size: 19, weight: .semibold))
                        HStack(spacing: 12) {
                            Text(entry.termName)
                            if let credit = entry.credit {
                                Text("\(String(format: "%g", credit)) 学分")
                            }
                            if let mode = entry.scoreMode {
                                Text(mode)
                            }
                        }
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                    }
                }

                if loading {
                    WKStatusCard(kind: .loading, message: "正在读取分项成绩…")
                } else if let error {
                    WKStatusCard(kind: .error, message: error)
                } else if let detail {
                    if !detail.parts.isEmpty {
                        GlassCard {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Text("分项成绩")
                                        .font(.system(size: 16, weight: .semibold))
                                    Spacer()
                                    if let total = detail.total {
                                        Text("总评 \(total)")
                                            .font(.system(size: 13, weight: .semibold))
                                    }
                                }
                                ForEach(detail.parts) { part in
                                    HStack {
                                        Text(part.name)
                                            .font(.system(size: 14))
                                        if let weight = part.weight {
                                            Text("\(String(format: "%g", weight))%")
                                                .font(.system(size: 11))
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        Text(part.score ?? "—")
                                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    }
                                }
                            }
                        }
                    }

                    if !detail.ranks.isEmpty {
                        GlassCard {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("成绩排名")
                                    .font(.system(size: 16, weight: .semibold))
                                ForEach(detail.ranks) { rank in
                                    HStack(alignment: .firstTextBaseline) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(rank.type)
                                                .font(.system(size: 13))
                                            Text(rank.name)
                                                .font(.system(size: 11))
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        if let position = rank.rank, let total = rank.total {
                                            Text("\(position) / \(total)")
                                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if detail.parts.isEmpty && detail.ranks.isEmpty {
                        WKStatusCard(kind: .empty, message: "这门课没有分项成绩与排名。")
                    }
                }
            }
            .navigationTitle("单科分析")
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
            detail = await store.gradeDetail(for: entry)
            if detail == nil { error = "教务没有返回这门课的分项成绩。" }
            loading = false
        }
    }
}

#Preview {
    NavigationStack {
        GradesView()
    }
    .environment(AppSettings.shared)
}
