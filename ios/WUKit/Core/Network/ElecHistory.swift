import Foundation

/// 一次电量读数。安卓同样把每次查询落盘，用「已用电量」的增量算日均。
struct ElecSnapshot: Codable, Identifiable, Hashable {
    var recordedAt: Date
    var remainingKwh: Double
    var usedKwh: Double?
    var updatedAt: String?

    var id: TimeInterval { recordedAt.timeIntervalSince1970 }
}

/// 由本机读数推出来的用电统计。
struct ElecStats {
    var sampleCount = 0
    var dailyUsageKwh: Double?
    var daysLeft: Double?
    var monthUsageKwh: Double?
    var monthCostYuan: Double?
}

/// 读数历史存在 UserDefaults，按「楼栋-房间」分开，换宿舍不会串数据。
enum ElecHistory {
    /// 校内电价，与安卓的 ELEC_PRICE_PER_KWH 一致。
    static let pricePerKwh = 0.63
    private static let limit = 400

    private static func key(building: String, room: String) -> String {
        "wk.elec.history.\(building)-\(room)"
    }

    static func load(building: String, room: String) -> [ElecSnapshot] {
        guard let data = UserDefaults.standard.data(forKey: key(building: building, room: room)),
              let list = try? JSONDecoder().decode([ElecSnapshot].self, from: data)
        else { return [] }
        return list
    }

    /// 读数没变就不重复记，否则同一天点几次刷新会把曲线压平。
    @discardableResult
    static func append(
        _ info: ElecInfo,
        building: String,
        room: String,
        now: Date = Date()
    ) -> [ElecSnapshot] {
        var list = load(building: building, room: room)
        if let last = list.last,
           last.updatedAt == info.updatedAt,
           abs(last.remainingKwh - info.remainingKwh) < 0.001,
           sameReading(last.usedKwh, info.usedKwh) {
            return list
        }
        list.append(
            ElecSnapshot(
                recordedAt: now,
                remainingKwh: info.remainingKwh,
                usedKwh: info.usedKwh,
                updatedAt: info.updatedAt
            )
        )
        if list.count > limit { list = Array(list.suffix(limit)) }
        if let data = try? JSONEncoder().encode(list) {
            UserDefaults.standard.set(data, forKey: key(building: building, room: room))
        }
        return list
    }

    static func computeStats(
        _ history: [ElecSnapshot],
        remainingKwh: Double,
        now: Date = Date()
    ) -> ElecStats {
        let samples = history.sorted { $0.recordedAt < $1.recordedAt }
        let weekAgo = now.addingTimeInterval(-7 * 24 * 3600)
        let daily = dailyUsage(samples.filter { $0.recordedAt >= weekAgo }) ?? dailyUsage(samples)
        let monthUsage = usageDelta(samples.filter { $0.recordedAt >= monthStart(now) })
        return ElecStats(
            sampleCount: samples.count,
            dailyUsageKwh: daily,
            daysLeft: daysLeft(remaining: remainingKwh, daily: daily),
            monthUsageKwh: monthUsage,
            monthCostYuan: monthUsage.map { $0 * pricePerKwh }
        )
    }

    private static func daysLeft(remaining: Double, daily: Double?) -> Double? {
        guard remaining > 0, let daily, daily > 0.01 else { return nil }
        return remaining / daily
    }

    private static func sameReading(_ lhs: Double?, _ rhs: Double?) -> Bool {
        switch (lhs, rhs) {
        case (nil, nil): return true
        case let (a?, b?): return abs(a - b) < 0.001
        default: return false
        }
    }

    private static func usageDelta(_ samples: [ElecSnapshot]) -> Double? {
        let used = samples.compactMap(\.usedKwh)
        guard used.count >= 2, let first = used.first, let last = used.last else { return nil }
        let delta = last - first
        return delta >= 0 ? delta : nil
    }

    private static func dailyUsage(_ samples: [ElecSnapshot]) -> Double? {
        let points = samples.filter { $0.usedKwh != nil }.sorted { $0.recordedAt < $1.recordedAt }
        guard points.count >= 2,
              let first = points.first,
              let last = points.last,
              let start = first.usedKwh,
              let end = last.usedKwh
        else { return nil }
        let hours = last.recordedAt.timeIntervalSince(first.recordedAt) / 3600
        // 少于 6 小时的样本推不出日速度，容易被一次抄表抖动带偏。
        guard hours >= 6 else { return nil }
        let delta = end - start
        guard delta >= 0 else { return nil }
        return delta / (hours / 24)
    }

    private static func monthStart(_ date: Date) -> Date {
        let parts = Calendar.current.dateComponents([.year, .month], from: date)
        return Calendar.current.date(from: parts) ?? date
    }
}
