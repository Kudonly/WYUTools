import Foundation
import Observation

/// 体育馆预约。场馆按天查、记录按人查，预约前先把统一认证 Cookie 换成应用态会话。
@Observable
final class GymStore {
    static let shared = GymStore()

    private enum Key {
        static let phone = "wk.gym.phone"
    }

    private let defaults = UserDefaults.standard

    var date: Date = Date()
    var venues: [GymVenue] = []
    /// 体育项目筛选，空串表示全部。
    var sportFilter = ""
    var records: [GymRecord] = []

    var loading = false
    var error: String?
    var recordsLoading = false
    var recordsError: String?

    var user: GymUser?
    var phone: String {
        didSet { defaults.set(phone, forKey: Key.phone) }
    }

    var booking = false
    var bookingError: String?
    var notice: String?

    private(set) var didLoadRecords = false

    private init() {
        phone = defaults.string(forKey: Key.phone) ?? ""
    }

    func loadVenues(date: Date? = nil) async {
        guard !loading else { return }
        if let date { self.date = date }
        loading = true
        error = nil
        do {
            if user == nil { user = try await GymAPI.shared.validateSession() }
            venues = try await GymAPI.shared.fetchVenues(date: self.date)
        } catch {
            self.error = error.localizedDescription
            if (error as? SsoError)?.sessionExpired == true {
                AuthStore.shared.noteSessionExpired()
            }
        }
        loading = false
    }

    func loadRecords() async {
        guard !recordsLoading else { return }
        recordsLoading = true
        recordsError = nil
        do {
            if user == nil { user = try await GymAPI.shared.validateSession() }
            records = try await GymAPI.shared.fetchRecords()
            didLoadRecords = true
        } catch {
            recordsError = error.localizedDescription
            if (error as? SsoError)?.sessionExpired == true {
                AuthStore.shared.noteSessionExpired()
            }
        }
        recordsLoading = false
    }

    func book(venue: GymVenue, slot: GymSlot) async {
        guard let user, !booking else { return }
        booking = true
        bookingError = nil
        notice = nil
        do {
            let record = try await GymAPI.shared.book(
                user: user,
                venue: venue,
                slot: slot,
                date: date,
                phone: phone
            )
            if let record {
                notice = "已预约 \(record.venueName) \(record.slot)"
            } else {
                notice = "提交成功但列表还没刷新，请到「我的预约」确认。"
            }
            await loadRecords()
            await loadVenues()
        } catch {
            bookingError = error.localizedDescription
        }
        booking = false
    }

    func cancel(_ record: GymRecord) async {
        guard !booking else { return }
        booking = true
        bookingError = nil
        do {
            try await GymAPI.shared.cancel(wid: record.wid)
            notice = "已取消 \(record.venueName) \(record.slot)"
            await loadRecords()
            await loadVenues()
        } catch {
            bookingError = error.localizedDescription
        }
        booking = false
    }
}
