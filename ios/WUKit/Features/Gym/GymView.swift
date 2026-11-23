import SwiftUI

/// 体育馆预约：按天查场馆 → 挑时段 → 提交，附「我的预约」。
struct GymView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = GymStore.shared
    @State private var auth = AuthStore.shared
    @State private var showLogin = false
    @State private var pending: (venue: GymVenue, slot: GymSlot)?
    @State private var showConfirm = false

    private let calendar = Calendar.current

    var body: some View {
        WKPageScroll {
            if !auth.loggedIn {
                WKLoginRequiredCard(message: "体育馆走一站式服务大厅，要先登录统一身份认证。") {
                    showLogin = true
                }
            } else {
                dayPicker
                sportFilterRow

                if store.loading && store.venues.isEmpty {
                    WKStatusCard(kind: .loading, message: "正在查询 \(dayLabel(store.date)) 的场地…")
                }
                if let error = store.error {
                    WKStatusCard(kind: .error, message: error) {
                        Task { await store.loadVenues() }
                    }
                }
                if !store.loading && store.error == nil && filteredVenues.isEmpty {
                    WKStatusCard(
                        kind: .empty,
                        message: store.sportFilter.isEmpty
                            ? "这天没有开放预约的场地，换一天看看。"
                            : "没有符合筛选条件的场地。"
                    )
                }

                ForEach(filteredVenues) { venue in
                    venueCard(venue)
                }

                recordsSection

                if let notice = store.notice {
                    Text(notice)
                        .font(.system(size: 12))
                        .foregroundStyle(.green)
                        .padding(.horizontal, 4)
                }
                if let error = store.bookingError {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(.red)
                        .padding(.horizontal, 4)
                }
            }
        }
        .navigationTitle("体育馆")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if auth.loggedIn {
                    Button {
                        Task {
                            await store.loadVenues()
                            await store.loadRecords()
                        }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .disabled(store.loading)
                }
            }
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
        }
        .sheet(isPresented: $showConfirm, onDismiss: { pending = nil }) {
            if let pending {
                GymConfirmSheet(venue: pending.venue, slot: pending.slot, date: store.date)
            }
        }
        .task {
            guard auth.loggedIn else { return }
            if store.venues.isEmpty { await store.loadVenues() }
            if !store.didLoadRecords { await store.loadRecords() }
        }
    }

    // MARK: - 日期

    /// 只给未来 5 天，和安卓一样一格占一份宽度、整行铺满。
    private var dayPicker: some View {
        HStack(spacing: 8) {
            ForEach(0..<5, id: \.self) { offset in
                let date = calendar.date(byAdding: .day, value: offset, to: calendar.startOfDay(for: Date())) ?? Date()
                let selected = calendar.isDate(date, inSameDayAs: store.date)
                Button {
                    Task { await store.loadVenues(date: date) }
                } label: {
                    VStack(spacing: 2) {
                        Text(offset == 0 ? "今天" : weekday(date))
                            .font(.system(size: 11, weight: .medium))
                        Text(dayOnly(date))
                            .font(.system(size: 15, weight: .semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(
                        selected ? AnyShapeStyle(settings.accentColor) : AnyShapeStyle(Color.primary.opacity(0.06)),
                        in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                    )
                    .foregroundStyle(selected ? Color.white : Color.primary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 2)
        .padding(.vertical, 2)
    }

    // MARK: - 体育项目

    /// 场地里出现过的项目名，去重后按字母序，选项多于一个才显示筛选行。
    private var sportOptions: [String] {
        let names = store.venues.compactMap { venue -> String? in
            guard let name = venue.sportName?.trimmingCharacters(in: .whitespaces), !name.isEmpty else {
                return nil
            }
            return name
        }
        return Array(Set(names)).sorted()
    }

    private var filteredVenues: [GymVenue] {
        let project = store.sportFilter
        guard !project.isEmpty else { return store.venues }
        return store.venues.filter { $0.sportName == project }
    }

    @ViewBuilder
    private var sportFilterRow: some View {
        let options = sportOptions
        if options.count > 1 {
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    sportChip("全部", active: store.sportFilter.isEmpty) {
                        store.sportFilter = ""
                    }
                    ForEach(options, id: \.self) { name in
                        sportChip(name, active: store.sportFilter == name) {
                            store.sportFilter = name
                        }
                    }
                }
                .padding(.horizontal, 2)
                .padding(.vertical, 2)
            }
            .scrollIndicators(.hidden)
        }
    }

    private func sportChip(_ title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button {
            withAnimation(.snappy(duration: 0.2)) { action() }
            WKHaptics.selection()
        } label: {
            Text(title)
                .font(.system(size: 12, weight: active ? .semibold : .medium))
                .lineLimit(1)
                .padding(.horizontal, 13)
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

    private func weekday(_ date: Date) -> String {
        let names = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"]
        return names[calendar.component(.weekday, from: date) - 1]
    }

    private func dayOnly(_ date: Date) -> String {
        let parts = calendar.dateComponents([.month, .day], from: date)
        return "\(parts.month ?? 0)/\(parts.day ?? 0)"
    }

    private func dayLabel(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M 月 d 日"
        return formatter.string(from: date)
    }

    // MARK: - 场馆

    private func venueCard(_ venue: GymVenue) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(venue.name)
                        .font(.system(size: 17, weight: .semibold))
                    if let type = venue.venueType {
                        WKChip(text: type, tint: settings.accentColor)
                    }
                    Spacer()
                    if let status = venue.openStatus {
                        Text(status)
                            .font(.system(size: 12))
                            .foregroundStyle(status.contains("开放") ? Color.green : Color.secondary)
                    }
                }

                HStack(spacing: 10) {
                    if let sport = venue.sportName {
                        Text(sport)
                    }
                    if let address = venue.address {
                        Text(address)
                    }
                    if venue.capacity > 0 {
                        Text("限 \(venue.capacity) 人")
                    }
                }
                .font(.system(size: 12))
                .foregroundStyle(.secondary)

                if venue.slots.isEmpty {
                    Text("当天没有可预约时段。")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                } else {
                    LazyVGrid(
                        columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3),
                        spacing: 8
                    ) {
                        ForEach(venue.slots) { slot in
                            Button {
                                pending = (venue, slot)
                                showConfirm = true
                            } label: {
                                Text(slot.time)
                                    .font(.system(size: 12, weight: .medium))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 34)
                                    .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .disabled(store.booking)
                        }
                    }
                }
            }
        }
    }

    // MARK: - 我的预约

    private var recordsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            WKSectionTitle(text: "我的预约")
                .padding(.horizontal, -2)
                .padding(.top, 8)

            if store.recordsLoading && store.records.isEmpty {
                WKStatusCard(kind: .loading, message: "正在读取预约记录…")
            } else if store.records.isEmpty {
                GlassCard {
                    Text("还没有预约记录。")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
            } else {
                ForEach(store.records) { record in
                    GlassCard {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .firstTextBaseline) {
                                Text(record.venueName)
                                    .font(.system(size: 16, weight: .semibold))
                                Spacer()
                                WKChip(
                                    text: record.status,
                                    tint: record.statusCode == "001" ? .green : .secondary
                                )
                            }
                            HStack(spacing: 10) {
                                Text(record.dateRaw)
                                Text(record.slot)
                                if let semester = record.semester { Text(semester) }
                            }
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)

                            if record.canCancel {
                                Button("取消预约") {
                                    Task { await store.cancel(record) }
                                }
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.red)
                                .disabled(store.booking)
                            }
                        }
                    }
                }
            }
        }
    }
}

/// 预约确认：手机号必填，提交后按服务端返回提示结果。
struct GymConfirmSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var store = GymStore.shared

    let venue: GymVenue
    let slot: GymSlot
    let date: Date

    var body: some View {
        NavigationStack {
            WKPageScroll {
                GlassCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(venue.name)
                            .font(.system(size: 18, weight: .semibold))
                        WKInfoRow(label: "日期", value: GymAPI.dayString(date))
                        WKInfoRow(label: "时段", value: slot.time)
                        if let sport = venue.sportName {
                            WKInfoRow(label: "项目", value: sport)
                        }
                        if let type = venue.venueType {
                            WKInfoRow(label: "类型", value: type)
                        }
                        if let address = venue.address {
                            WKInfoRow(label: "地点", value: address)
                        }
                    }
                }

                GlassCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("联系电话")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                        TextField("手机号", text: Binding(
                            get: { store.phone },
                            set: { store.phone = $0 }
                        ))
                        .keyboardType(.numberPad)
                        .font(.system(size: 16, weight: .medium))
                        Text("体育馆按人限约，取消要看时段是否已开始。")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                }

                if let error = store.bookingError {
                    Text(error).font(.system(size: 13)).foregroundStyle(.red)
                }

                WKProminentButton(
                    title: store.booking ? "提交中…" : "确认预约",
                    symbol: "checkmark.circle",
                    enabled: !store.booking && store.phone.count >= 6
                ) {
                    Task {
                        await store.book(venue: venue, slot: slot)
                        if store.bookingError == nil { dismiss() }
                    }
                }
            }
            .navigationTitle("确认预约")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

#Preview {
    NavigationStack {
        GymView()
    }
    .environment(AppSettings.shared)
}
