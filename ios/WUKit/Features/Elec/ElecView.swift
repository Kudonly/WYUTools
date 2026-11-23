import SwiftUI
import UIKit
import CoreImage.CIFilterBuiltins

/// 电费：查剩余电量 → 统计（日均 / 可用天数 / 本月用电）→ 充值 → 记录。
/// 版式对齐 Android 的 ElecScreen：查询卡、结果卡、充值入口、用电趋势、充值记录。
struct ElecView: View {
    @Environment(AppSettings.self) private var settings
    @State private var store = ElecStore.shared
    @State private var showPay = false

    var body: some View {
        WKPageScroll {
            if !store.canQuery || store.info == nil {
                queryCard
            }
            if let info = store.info {
                balanceCard(info)
            }
            if let error = store.error {
                WKStatusCard(kind: .error, message: error) {
                    Task { await store.refresh() }
                }
            }
            if store.loading && store.info == nil {
                WKStatusCard(kind: .loading, message: "正在查询电量…")
            }

            if store.canQuery {
                rechargeCard
            }

            trendCard
            historyCard

            Text("电费接口为校园内网，需连接校园 Wi-Fi 或 VPN。")
                .font(.system(size: 12))
                .foregroundStyle(.tertiary)
                .padding(.horizontal, 4)
        }
        .navigationTitle("电费")
        .navigationBarTitleDisplayMode(.inline)
        .wkFullScreenDetail()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await store.refresh() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(store.loading || !store.canQuery)
            }
        }
        .sheet(isPresented: $showPay) {
            ElecPaySheet()
        }
        .task {
            store.loadSnapshots()
            if store.info == nil && store.canQuery {
                await store.refresh()
            }
        }
    }

    // MARK: - 房源

    private var queryCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("输入宿舍信息")
                    .font(.system(size: 16, weight: .semibold))

                HStack(spacing: 12) {
                    field("楼栋", text: Binding(
                        get: { store.building },
                        set: { store.building = $0 }
                    ), placeholder: "如 1")
                    field("房间号", text: Binding(
                        get: { store.room },
                        set: { store.room = $0 }
                    ), placeholder: "如 204")
                }

                if let error = store.error {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(.red)
                }

                WKProminentButton(
                    title: store.loading ? "查询中…" : "查询电费",
                    symbol: "bolt.fill",
                    enabled: store.canQuery && !store.loading
                ) {
                    Task { await store.refresh() }
                }
            }
        }
    }

    private func field(
        _ title: String,
        text: Binding<String>,
        placeholder: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            TextField(placeholder, text: text)
                .keyboardType(.numbersAndPunctuation)
                .font(.system(size: 16, weight: .medium))
                .padding(.horizontal, 12)
                .frame(height: 42)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - 余额

    private func balanceCard(_ info: ElecInfo) -> some View {
        let low = info.remainingKwh < 20
        let stats = store.stats
        return GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .center, spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(settings.accentColor.opacity(0.12))
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(settings.accentColor)
                    }
                    .frame(width: 40, height: 40)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("剩余电量")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                        if let name = info.buildingName, let room = info.roomName, !name.isEmpty {
                            Text("\(name) · \(room)")
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer(minLength: 8)
                    WKChip(text: low ? "电量偏低" : "电量正常", tint: low ? .orange : .green)
                }

                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(String(format: "%.2f", info.remainingKwh))
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                        .foregroundStyle(low ? Color.orange : Color.primary)
                    Text("度 (kWh)")
                        .font(.system(size: 14))
                        .foregroundStyle(.secondary)
                }

                if low {
                    Label("电量偏低，请及时充值", systemImage: "exclamationmark.triangle.fill")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.orange)
                }

                WKDivider()

                WKInfoRow(label: "抄表时间", value: info.updatedAt ?? "—")
                WKInfoRow(
                    label: "折合电费",
                    value: String(format: "≈ ¥%.2f", info.remainingKwh * ElecHistory.pricePerKwh)
                )
                WKInfoRow(
                    label: "近期日均",
                    value: stats.dailyUsageKwh.map { formatKwh($0) + " 度" } ?? "—"
                )
                WKInfoRow(
                    label: "预计可用",
                    value: stats.daysLeft.map { formatDays($0) } ?? "—"
                )

                Text("折合电费按 0.63 元/度估算；日均与可用天数来自本机查询记录")
                    .font(.system(size: 11))
                    .foregroundStyle(.tertiary)
            }
        }
    }

    // MARK: - 充值

    private var rechargeCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Image(systemName: "qrcode")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(settings.accentColor)
                    Text("电费充值")
                        .font(.system(size: 16, weight: .semibold))
                    Spacer()
                    Text("微信 / 支付宝")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }

                Text("输入充值金额，生成学校电费系统支付二维码，扫码完成支付。")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)

                WKProminentButton(title: "充值电费", symbol: "qrcode", enabled: store.canQuery) {
                    showPay = true
                }
                .padding(.top, 4)
            }
        }
    }

    // MARK: - 用电趋势

    private var trendCard: some View {
        let recent = Array(store.snapshots.suffix(30))
        let stats = store.stats
        return GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "chart.xyaxis.line")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(settings.accentColor)
                    Text("用电趋势")
                        .font(.system(size: 16, weight: .semibold))
                    Spacer()
                    Text("本机记录")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }

                if recent.count >= 2 {
                    sparkline(recent.map(\.remainingKwh))
                        .frame(height: 92)

                    HStack(spacing: 8) {
                        Text(recordTime(recent.first?.recordedAt))
                        Spacer()
                        Text("近 \(recent.count) 次查询")
                        Spacer()
                        Text(recordTime(recent.last?.recordedAt))
                    }
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)

                    WKDivider()
                } else {
                    Text("每次查询会自动记录读数，积累 2 条以上后显示趋势。")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 8) {
                    statCell("近 7 日日均", stats.dailyUsageKwh.map { formatKwh($0) + " 度" } ?? "—")
                    statCell("本月用电", stats.monthUsageKwh.map { formatKwh($0) + " 度" } ?? "—")
                    statCell("本月电费", stats.monthCostYuan.map { String(format: "¥%.2f", $0) } ?? "—")
                }

                if let daysLeft = stats.daysLeft {
                    Text("按近期用电速度，当前剩余电量约可用 \(formatDays(daysLeft))")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private func statCell(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 13, weight: .medium))
            Text(label)
                .font(.system(size: 10))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    /// 剩余电量折线 + 淡淡的面积填充。
    private func sparkline(_ values: [Double]) -> some View {
        GeometryReader { proxy in
            let minValue = values.min() ?? 0
            let maxValue = values.max() ?? 1
            let span = max(maxValue - minValue, 0.5)
            let pad: CGFloat = 6
            let height = max(1, proxy.size.height - pad * 2)
            let stepX = proxy.size.width / CGFloat(max(1, values.count - 1))

            ZStack {
                Path { path in
                    for (index, value) in values.enumerated() {
                        let point = CGPoint(
                            x: stepX * CGFloat(index),
                            y: pad + (1 - CGFloat((value - minValue) / span)) * height
                        )
                        if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                    }
                }
                .stroke(
                    settings.accentColor,
                    style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
                )

                Path { path in
                    guard values.count > 1 else { return }
                    let bottom = proxy.size.height
                    path.move(to: CGPoint(x: 0, y: bottom))
                    for (index, value) in values.enumerated() {
                        let point = CGPoint(
                            x: stepX * CGFloat(index),
                            y: pad + (1 - CGFloat((value - minValue) / span)) * height
                        )
                        path.addLine(to: point)
                    }
                    path.addLine(to: CGPoint(x: stepX * CGFloat(values.count - 1), y: bottom))
                    path.closeSubpath()
                }
                .fill(
                    LinearGradient(
                        colors: [settings.accentColor.opacity(0.20), settings.accentColor.opacity(0.02)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            }
        }
    }

    // MARK: - 记录

    private var historyCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("充值记录")
                        .font(.system(size: 16, weight: .semibold))
                    Spacer()
                    Picker("", selection: Binding(
                        get: { store.period },
                        set: { period in
                            store.period = period
                            Task { await store.loadHistory(period: period) }
                        }
                    )) {
                        ForEach(ElecPeriod.allCases) { period in
                            Text(period.label).tag(period)
                        }
                    }
                    .pickerStyle(.menu)
                    .labelsHidden()
                }

                if store.historyLoading {
                    HStack(spacing: 8) {
                        ProgressView().controlSize(.small)
                        Text("正在读取…").font(.system(size: 13)).foregroundStyle(.secondary)
                    }
                } else if let page = store.history {
                    if page.records.isEmpty {
                        Text(store.canQuery ? "这段时间没有充值记录。" : "先查询一次电量，再来看充值记录。")
                            .font(.system(size: 13))
                            .foregroundStyle(.secondary)
                    } else {
                        HStack(spacing: 16) {
                            WKStatTile(title: "合计", value: String(format: "%.0f", page.totalAmountYuan), unit: "元")
                            WKStatTile(
                                title: "笔数",
                                value: "\(page.total)",
                                unit: "笔",
                                tint: .secondary
                            )
                            WKStatTile(
                                title: "笔均",
                                value: String(format: "%.0f", page.averageAmountYuan),
                                unit: "元",
                                tint: .secondary
                            )
                        }

                        rechargeBars(page.records)

                        WKDivider()
                        ForEach(page.records.prefix(20)) { record in
                            HStack(alignment: .firstTextBaseline) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(recordTime(record.paidAt, fallback: record.paidAtRaw))
                                        .font(.system(size: 13))
                                    Text(record.method)
                                        .font(.system(size: 11))
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text(String(format: "+%.2f", record.amountYuan))
                                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                            }
                            .padding(.vertical, 2)
                        }
                    }
                } else if let error = store.historyError {
                    Text(error)
                        .font(.system(size: 13))
                        .foregroundStyle(.red)
                } else {
                    Button("读取充值记录") {
                        Task { await store.loadHistory() }
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .disabled(!store.canQuery)
                }
            }
        }
    }

    /// 最近 6 个月的充值柱状图，与安卓的 RechargeMonthlyBars 同一套比例。
    @ViewBuilder
    private func rechargeBars(_ records: [ElecRechargeRecord]) -> some View {
        let bars = monthBars(records)
        if !bars.isEmpty {
            let peak = max(bars.map(\.amount).max() ?? 1, 1)
            VStack(alignment: .leading, spacing: 8) {
                Text("按月充值")
                    .font(.system(size: 14, weight: .semibold))
                HStack(alignment: .bottom, spacing: 10) {
                    ForEach(bars) { bar in
                        VStack(spacing: 4) {
                            Text(String(format: "%.0f", bar.amount))
                                .font(.system(size: 9))
                                .foregroundStyle(.secondary)
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(settings.accentColor.opacity(0.75))
                                .frame(width: 26, height: CGFloat(12 + 70 * (bar.amount / peak)))
                            Text(bar.label)
                                .font(.system(size: 9))
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                .frame(height: 130, alignment: .bottom)
            }
        }
    }

    private struct MonthBar: Identifiable {
        var label: String
        var amount: Double
        var id: String { label }
    }

    private func monthBars(_ records: [ElecRechargeRecord]) -> [MonthBar] {
        var buckets: [Date: Double] = [:]
        let calendar = Calendar.current
        for record in records {
            guard let date = record.paidAt else { continue }
            let parts = calendar.dateComponents([.year, .month], from: date)
            guard let month = calendar.date(from: parts) else { continue }
            buckets[month, default: 0] += record.amountYuan
        }
        return buckets.keys.sorted().suffix(6).map { month in
            MonthBar(
                label: "\(calendar.component(.month, from: month))月",
                amount: buckets[month] ?? 0
            )
        }
    }

    // MARK: - 格式化

    private func formatKwh(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(format: "%.2f", value)
    }

    private func formatDays(_ days: Double) -> String {
        if days >= 100 { return "99+ 天" }
        if days >= 10 { return "\(Int(days)) 天" }
        return String(format: "%.1f 天", days)
    }

    private func recordTime(_ date: Date?, fallback: String = "") -> String {
        guard let date else { return fallback.isEmpty ? "—" : fallback }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = Calendar.current.component(.year, from: date) == Calendar.current.component(.year, from: Date())
            ? "MM-dd HH:mm"
            : "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
}

/// 充值：金额 → 生成二维码 → 轮询支付状态。
struct ElecPaySheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var settings
    @State private var store = ElecStore.shared
    @State private var amount = 30
    @State private var channel: ElecPayChannel = .wechat
    @State private var resultText: String?

    private let amounts = [10, 20, 30, 50, 100, 200]

    var body: some View {
        NavigationStack {
            WKPageScroll {
                GlassCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("充值金额")
                            .font(.system(size: 16, weight: .semibold))
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                            ForEach(amounts, id: \.self) { value in
                                Button {
                                    amount = value
                                } label: {
                                    Text("\(value) 元")
                                        .font(.system(size: 15, weight: .semibold))
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 44)
                                        .background(
                                            amount == value ? settings.accentColor : Color.primary.opacity(0.06),
                                            in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        )
                                        .foregroundStyle(amount == value ? Color.white : Color.primary)
                                }
                                .buttonStyle(.plain)
                            }
                        }

                        Picker("支付方式", selection: $channel) {
                            ForEach(ElecPayChannel.allCases) { item in
                                Text(item.label).tag(item)
                            }
                        }
                        .pickerStyle(.segmented)

                        WKProminentButton(
                            title: store.paying ? "正在下单…" : "生成支付二维码",
                            symbol: "qrcode",
                            enabled: !store.paying
                        ) {
                            resultText = nil
                            Task {
                                await store.createOrder(amountYuan: amount, channel: channel)
                                if let order = store.order {
                                    resultText = "等待支付…"
                                    let result = await store.waitForPayment(order: order)
                                    switch result {
                                    case .success:
                                        resultText = "支付成功，电量稍后到账。"
                                        await store.refresh()
                                    case .failed:
                                        resultText = "支付失败，请重新下单。"
                                    case .pending:
                                        resultText = "还没查到支付结果，可稍后再看充值记录。"
                                    }
                                }
                            }
                        }

                        if let error = store.payError {
                            Text(error).font(.system(size: 13)).foregroundStyle(.red)
                        }
                    }
                }

                if let order = store.order, let image = QRCode.image(order.qrContent) {
                    GlassCard {
                        VStack(spacing: 10) {
                            Image(uiImage: image)
                                .interpolation(.none)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 220, height: 220)
                            Text("用微信 / 支付宝扫一扫")
                                .font(.system(size: 13))
                                .foregroundStyle(.secondary)
                            if let resultText {
                                Text(resultText)
                                    .font(.system(size: 13, weight: .medium))
                            }
                            Text("二维码 \(order.expireSeconds / 60) 分钟内有效")
                                .font(.system(size: 11))
                                .foregroundStyle(.tertiary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            .navigationTitle("电费充值")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") {
                        store.clearOrder()
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.large])
    }
}

enum QRCode {
    static func image(_ text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

#Preview {
    NavigationStack {
        ElecView()
    }
    .environment(AppSettings.shared)
}
