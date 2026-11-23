import Foundation
import Observation

/// 电费：只记住上次查过的楼栋房号，数据和错误都留在页面里。
@Observable
final class ElecStore {
    static let shared = ElecStore()

    private enum Key {
        static let building = "wk.elec.building"
        static let room = "wk.elec.room"
    }

    private let defaults = UserDefaults.standard

    var building: String {
        didSet { defaults.set(building, forKey: Key.building) }
    }
    var room: String {
        didSet { defaults.set(room, forKey: Key.room) }
    }

    var info: ElecInfo?
    var loading = false
    var error: String?

    /// 本机读数快照与由它推出来的用电统计。
    private(set) var snapshots: [ElecSnapshot] = []
    private(set) var stats = ElecStats()
    private(set) var lastQueryAt: Date?

    var history: ElecRechargePage?
    var historyLoading = false
    var historyError: String?
    var period: ElecPeriod = .oneYear

    var paying = false
    var payError: String?
    var order: ElecRechargeOrder?

    var canQuery: Bool {
        !building.trimmingCharacters(in: .whitespaces).isEmpty
            && !room.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private init() {
        building = defaults.string(forKey: Key.building) ?? ""
        room = defaults.string(forKey: Key.room) ?? ""
    }

    func refresh() async {
        guard canQuery, !loading else { return }
        loading = true
        error = nil
        let house = building.trimmingCharacters(in: .whitespaces)
        let roomNo = room.trimmingCharacters(in: .whitespaces)
        do {
            let result = try await ElecAPI.query(building: house, room: roomNo)
            info = result
            lastQueryAt = Date()
            snapshots = ElecHistory.append(result, building: house, room: roomNo)
            stats = ElecHistory.computeStats(snapshots, remainingKwh: result.remainingKwh)
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    /// 冷启动先把本机读数读出来，曲线不用等一次查询。
    func loadSnapshots() {
        let house = building.trimmingCharacters(in: .whitespaces)
        let roomNo = room.trimmingCharacters(in: .whitespaces)
        guard !house.isEmpty, !roomNo.isEmpty else {
            snapshots = []
            stats = ElecStats()
            return
        }
        snapshots = ElecHistory.load(building: house, room: roomNo)
        stats = ElecHistory.computeStats(snapshots, remainingKwh: info?.remainingKwh ?? 0)
    }

    func loadHistory(period: ElecPeriod? = nil) async {
        guard canQuery, !historyLoading else { return }
        if let period { self.period = period }
        historyLoading = true
        historyError = nil
        do {
            history = try await ElecAPI.rechargeHistory(
                building: building.trimmingCharacters(in: .whitespaces),
                room: room.trimmingCharacters(in: .whitespaces),
                period: self.period
            )
        } catch {
            historyError = error.localizedDescription
        }
        historyLoading = false
    }

    func createOrder(amountYuan: Int, channel: ElecPayChannel) async {
        guard canQuery, !paying else { return }
        paying = true
        payError = nil
        do {
            order = try await ElecAPI.createOrder(
                building: building.trimmingCharacters(in: .whitespaces),
                room: room.trimmingCharacters(in: .whitespaces),
                amountCent: amountYuan * 100,
                channel: channel
            )
        } catch {
            payError = error.localizedDescription
        }
        paying = false
    }

    /// 轮询支付结果，最多等 expireSeconds。
    func waitForPayment(order: ElecRechargeOrder) async -> ElecPayResult {
        let deadline = Date().addingTimeInterval(Double(order.expireSeconds) + 10)
        while Date() < deadline {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if let result = try? await ElecAPI.checkOrder(orderCode: order.orderCode) {
                if result != .pending { return result }
            }
        }
        return .pending
    }

    func clearOrder() {
        order = nil
        payError = nil
    }
}
