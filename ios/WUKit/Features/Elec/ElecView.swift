import SwiftUI
import UIKit
import CoreImage.CIFilterBuiltins

/// 电费：查询剩余电量、充值记录、扫码充值。
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
            if store.info == nil && store.canQuery {
                await store.refresh()
            }
        }
    }

    // MARK: - 房源

    private var queryCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("查询宿舍电量")
                    .font(.system(size: 16, weight: .semibold))

                HStack(spacing: 12) {
                    field("楼栋", text: Binding(
                        get: { store.building },
                        set: { store.building = $0 }
                    ), placeholder: "如 1")
                    field("房间", text: Binding(
                        get: { store.room },
                        set: { store.room = $0 }
                    ), placeholder: "如 204")
                }

                WKProminentButton(
                    title: store.loading ? "查询中…" : "查询",
                    symbol: "bolt.fill",
                    enabled: store.canQuery && !store.loading
                ) {
                    Task { await store.refresh() }
                }
            }
        }
    }

    private func field(_ title: String, text: Binding<String>, placeholder: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            TextField(placeholder, text: text)
                .keyboardType(.numbersAndPunctuation)
                .font(.system(size: 16, weight: .medium))
                .padding(.horizontal, 12)
                .frame(height: 42)
                .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    // MARK: - 余额

    private func balanceCard(_ info: ElecInfo) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(String(format: "%.2f", info.remainingKwh))
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                    Text("kWh")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(.secondary)
                    Spacer()
                    WKChip(
                        text: info.remainingKwh <= 10 ? "余额偏低" : "电量正常",
                        tint: info.remainingKwh <= 10 ? .orange : .green
                    )
                }

                Text("剩余电量")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)

                WKDivider()

                if let used = info.usedKwh {
                    WKInfoRow(label: "已用电量", value: String(format: "%.2f kWh", used))
                }
                if let name = info.buildingName, let room = info.roomName {
                    WKInfoRow(label: "房间", value: "\(name) · \(room)")
                }
                if let type = info.userTypeName {
                    WKInfoRow(label: "用电类型", value: type)
                }
                if let updated = info.updatedAt {
                    WKInfoRow(label: "更新时间", value: updated)
                }
            }
        }
    }

    // MARK: - 充值

    private var rechargeCard: some View {
        GlassCardButton(action: { showPay = true }) {
            HStack(spacing: 14) {
                Image(systemName: "qrcode")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(settings.accentColor)
                    .frame(width: 30)
                VStack(alignment: .leading, spacing: 3) {
                    Text("扫码充值")
                        .font(.system(size: 16, weight: .semibold))
                    Text("生成微信 / 支付宝二维码，用手机扫一下付款")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(WKInk.tertiary)
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
                        }
                        WKDivider()
                        ForEach(page.records.prefix(20)) { record in
                            HStack(alignment: .firstTextBaseline) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(record.paidAtRaw)
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
}

/// 扫码充值：金额 → 生成二维码 → 轮询支付状态。
struct ElecPaySheet: View {
    @Environment(\.dismiss) private var dismiss
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
                                            amount == value ? Color.accentColor : Color.primary.opacity(0.06),
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
