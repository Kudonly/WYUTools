import Foundation

struct ElecInfo {
    var remainingKwh: Double
    var usedKwh: Double?
    var schoolName: String?
    var buildingName: String?
    var roomName: String?
    var updatedAt: String?
    var userTypeName: String?
    var status: Int?
}

enum ElecPeriod: String, CaseIterable, Identifiable {
    case threeMonths = "3M"
    case oneYear = "1Y"
    case all = "1Y+"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .threeMonths: return "近 3 个月"
        case .oneYear: return "近 1 年"
        case .all: return "全部"
        }
    }
}

struct ElecRechargeRecord: Identifiable {
    var paidAtRaw: String
    var paidAt: Date?
    var amountYuan: Double
    var method: String
    var orderCode: String
    var id: String { "\(orderCode)|\(paidAtRaw)" }
}

struct ElecRechargePage {
    var records: [ElecRechargeRecord]
    var total: Int
    var totalAmountYuan: Double { records.reduce(0) { $0 + $1.amountYuan } }
    var averageAmountYuan: Double {
        records.isEmpty ? 0 : totalAmountYuan / Double(records.count)
    }
}

enum ElecPayChannel: String, CaseIterable, Identifiable {
    case wechat = "weixin"
    case alipay = "alipay"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .wechat: return "微信"
        case .alipay: return "支付宝"
        }
    }
}

struct ElecRechargeOrder {
    var orderCode: String
    var qrContent: String
    var expireSeconds: Int
}

enum ElecPayResult {
    case pending
    case success
    case failed
}

/// 电费查询：明文 HTTP，只认 Android UA。
enum ElecAPI {
    private static let base = "http://202.192.240.231"
    private static let userAgent = "WYUTools/1.0 (Android)"

    static func query(building: String, room: String) async throws -> ElecInfo {
        var lastMessage: String?
        do {
            if let info = try await postV2(building: building, room: room) { return info }
        } catch let error as SsoError {
            lastMessage = error.message
        }
        do {
            if let info = try await postV1(building: building, room: room) { return info }
        } catch let error as SsoError {
            if lastMessage == nil { lastMessage = error.message }
        }
        throw SsoError(message: lastMessage ?? "请输入正确的栋数和房间号")
    }

    private static func postV2(building: String, room: String) async throws -> ElecInfo? {
        let root = try await execute(
            path: "/scp-api/electricity-recharge/getCurrentRemaining_v2",
            fields: baseFields(building: building, room: room)
        )
        guard isSuccess(root) else { throw SsoError(message: errorMessage(root)) }
        guard let data = root["data"] as? [String: Any] else {
            throw SsoError(message: "服务器未返回电费数据")
        }
        guard let remaining = data.double("resamp") else {
            throw SsoError(message: "服务器未返回剩余电量")
        }
        return ElecInfo(
            remainingKwh: remaining,
            usedKwh: data.double("usedamp"),
            schoolName: data.string("schoolName"),
            buildingName: data.string("apartName"),
            roomName: data.string("roomName"),
            updatedAt: data.string("updatedt"),
            userTypeName: data.string("userTypeName"),
            status: data.int("status")
        )
    }

    private static func postV1(building: String, room: String) async throws -> ElecInfo? {
        let root = try await execute(
            path: "/scp-api/electricity-recharge/getCurrentRemaining",
            fields: baseFields(building: building, room: room)
        )
        guard isSuccess(root) else { throw SsoError(message: errorMessage(root)) }
        var remaining: Double?
        if let number = root["data"] as? NSNumber {
            remaining = number.doubleValue
        } else if let text = root.string("data") {
            remaining = Double(text)
        }
        guard let remaining else {
            throw SsoError(message: "服务器未返回剩余电量")
        }
        return ElecInfo(
            remainingKwh: remaining,
            buildingName: building.trimmingCharacters(in: .whitespaces),
            roomName: room.trimmingCharacters(in: .whitespaces)
        )
    }

    static func rechargeHistory(building: String, room: String, period: ElecPeriod) async throws -> ElecRechargePage {
        var records: [ElecRechargeRecord] = []
        var total = 0
        var page = 1
        repeat {
            let root = try await execute(
                path: "/scp-api/electricity-recharge/orderQueryWithPage",
                fields: baseFields(building: building, room: room) + [
                    ("payResult", "1"),
                    ("page", "\(page)"),
                    ("pageSize", "200"),
                    ("period", period.rawValue)
                ]
            )
            guard isSuccess(root) else { throw SsoError(message: errorMessage(root)) }
            guard let data = root["data"] as? [String: Any] else {
                throw SsoError(message: "服务器未返回充值记录")
            }
            total = data.int("total") ?? 0
            let items = (data["data"] as? [[String: Any]]) ?? []
            if items.isEmpty { break }
            for item in items {
                records.append(parseRecord(item))
            }
            if records.count >= total { break }
            page += 1
        } while page <= 5

        let sorted = records.sorted { $0.paidAtRaw > $1.paidAtRaw }
        let calendar = Calendar.current
        let filtered: [ElecRechargeRecord]
        switch period {
        case .threeMonths:
            let cutoff = calendar.date(byAdding: .month, value: -3, to: Date()) ?? Date()
            filtered = sorted.filter { $0.paidAt.map { $0 >= cutoff } ?? true }
        case .oneYear:
            let cutoff = calendar.date(byAdding: .year, value: -1, to: Date()) ?? Date()
            filtered = sorted.filter { $0.paidAt.map { $0 >= cutoff } ?? true }
        case .all:
            filtered = sorted
        }
        return ElecRechargePage(
            records: filtered,
            total: period == .all && total > 0 ? total : filtered.count
        )
    }

    static func createOrder(
        building: String,
        room: String,
        amountCent: Int,
        channel: ElecPayChannel
    ) async throws -> ElecRechargeOrder {
        let payload: [String: Any] = [
            "mobile": "",
            "building": building.trimmingCharacters(in: .whitespaces),
            "room": room.trimmingCharacters(in: .whitespaces),
            "payCent": amountCent,
            "userTypeId": 1,
            "codeType": channel.rawValue
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else {
            throw SsoError(message: "充值请求组装失败")
        }
        let root = try await executeJSON(
            path: "/scp-api/electricity-recharge/create-order-cb",
            body: data
        )
        if root.bool("success") == false {
            throw SsoError(message: errorMessage(root))
        }
        guard let info = root["data"] as? [String: Any] else {
            throw SsoError(message: "服务器未返回充值订单")
        }
        guard let orderCode = info.string("orderCode"), !orderCode.isEmpty,
              let qrContent = info.string("qrCode"), !qrContent.isEmpty
        else {
            throw SsoError(message: "充值二维码生成失败，请重试")
        }
        let expire = min(1800, max(30, info.int("qrCodeExpireTime") ?? 120))
        return ElecRechargeOrder(orderCode: orderCode, qrContent: qrContent, expireSeconds: expire)
    }

    static func checkOrder(orderCode: String) async throws -> ElecPayResult {
        let root = try await execute(
            path: "/scp-api/electricity-recharge/check-order-cb",
            fields: [("orderCode", orderCode)]
        )
        guard isSuccess(root) else { throw SsoError(message: errorMessage(root)) }
        guard let data = root["data"] as? [String: Any],
              let record = data["record"] as? [String: Any]
        else {
            throw SsoError(message: "暂未查询到支付结果")
        }
        switch record.int("payResult") {
        case 1: return .success
        case 2: return .failed
        default: return .pending
        }
    }

    // MARK: - 内部

    private static func parseRecord(_ item: [String: Any]) -> ElecRechargeRecord {
        let raw = item.string("payTime") ?? ""
        let amount = (item.double("payCent") ?? 0) / 100
        return ElecRechargeRecord(
            paidAtRaw: raw,
            paidAt: parsePayTime(raw),
            amountYuan: amount,
            method: methodLabel(item.string("payMethod") ?? ""),
            orderCode: item.string("orderCode") ?? ""
        )
    }

    private static func parsePayTime(_ raw: String) -> Date? {
        guard !raw.isEmpty else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        if let date = formatter.date(from: raw) { return date }
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return formatter.date(from: raw)
    }

    private static func methodLabel(_ code: String) -> String {
        switch code.trimmingCharacters(in: .whitespaces).lowercased() {
        case "code": return "扫码支付"
        case "wx", "wechat": return "微信支付"
        case "alipay": return "支付宝"
        case "": return "未知方式"
        default: return code
        }
    }

    private static func baseFields(building: String, room: String) -> [(String, String)] {
        [
            ("userTypeID", "1"),
            ("building", building.trimmingCharacters(in: .whitespaces)),
            ("room", room.trimmingCharacters(in: .whitespaces))
        ]
    }

    private static func isSuccess(_ root: [String: Any]) -> Bool {
        if root.bool("success") == true { return true }
        return root.int("code") == 10000
    }

    private static func errorMessage(_ root: [String: Any]) -> String {
        for key in ["message", "msg", "error"] {
            if let text = root.string(key), !text.isEmpty { return text }
        }
        if let code = root.int("code"), code >= 0 { return "查询失败（code=\(code)）" }
        return "查询失败"
    }

    private static func execute(path: String, fields: [(String, String)]) async throws -> [String: Any] {
        var request = URLRequest(url: URL(string: base + path)!)
        request.httpMethod = "POST"
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(
            "application/x-www-form-urlencoded; charset=UTF-8",
            forHTTPHeaderField: "Content-Type"
        )
        request.httpBody = fields
            .map { "\(escape($0.0))=\(escape($0.1))" }
            .joined(separator: "&")
            .data(using: .utf8)
        return try await send(request)
    }

    private static func executeJSON(path: String, body: Data) async throws -> [String: Any] {
        var request = URLRequest(url: URL(string: base + path)!)
        request.httpMethod = "POST"
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        return try await send(request)
    }

    private static func send(_ request: URLRequest) async throws -> [String: Any] {
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(status) else {
            throw SsoError(message: "服务器返回异常（HTTP \(status)）")
        }
        guard !data.isEmpty else { throw SsoError(message: "服务器返回为空") }
        guard let text = String(data: data, encoding: .utf8),
              let root = JSON.object(text)
        else {
            throw SsoError(message: "服务器返回数据异常")
        }
        return root
    }

    private static func escape(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}
