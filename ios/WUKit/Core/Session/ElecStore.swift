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
        do {
            info = try await ElecAPI.query(
                building: building.trimmingCharacters(in: .whitespaces),
                room: room.trimmingCharacters(in: .whitespaces)
            )
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
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
