import Foundation
import CryptoKit

struct LxtLoginResult {
    var token: String
}

struct LxtAreaNode: Identifiable, Hashable {
    var id: String
    var name: String
    var siteFlag: Int
}

struct LxtMachine: Identifiable, Hashable {
    var machineId: String
    var deviceMac: String
    var deviceVer: String
    var roomName: String
    var siteId: String
    var siteFlag: Int
    var id: String { machineId }
}

struct LxtMachineStatus: Hashable {
    var machineId: String
    var status: Int
}

struct LxtError: LocalizedError {
    var code: Int
    var message: String
    var errorDescription: String? { message }
}

/// 热水（第三方 SaaS）。所有请求都带 MD5 签名头与 X-Ghost 时间戳校验。
enum LxtAPI {
    static let host = "https://v3-prod-beta-app.lxt6.cn"
    static let schoolId = "80790"
    static let investorId = "600000000000000047"
    private static let signSalt = "1FF75E512"

    // MARK: - 登录

    /// 验证码是 base64 dataURL，返回解码后的图片。
    static func captchaImage(account: String) async throws -> Data {
        let envelope = try await ensure(
            call(method: "GET", path: "/user/authentication/getCode", params: [("account", account)])
        )
        guard let raw = envelope.data as? String else {
            throw LxtError(code: -1, message: "验证码获取失败")
        }
        let base64 = raw.components(separatedBy: "base64,").last ?? raw
        guard let data = Data(base64Encoded: base64, options: .ignoreUnknownCharacters) else {
            throw LxtError(code: -1, message: "验证码图片解析失败")
        }
        return data
    }

    static func login(account: String, password: String, code: String) async throws -> LxtLoginResult {
        let inner = jsonObject([
            ("studentMobile", account),
            ("loginPassword", password),
            ("code", code)
        ])
        let payload = jsonObject([
            ("data", Data(inner.utf8).base64EncodedString())
        ])
        let envelope = try await ensure(
            call(method: "POST", path: "/user/login/userLoginV2WithEncrypt", body: payload)
        )
        guard let token = envelope.token, !token.isEmpty else {
            throw LxtError(code: -1, message: "登录失败：未取到登录凭证")
        }
        return LxtLoginResult(token: token)
    }

    // MARK: - 余额

    /// 热水钱包，`money` 为分。
    static func queryHot(token: String) async throws -> [String: Any]? {
        let body = jsonObject([
            ("schoolId", schoolId),
            ("investorId", investorId),
            ("walletType", 1)
        ])
        let envelope = try await ensure(
            call(method: "POST", path: "/payment/student/getWalletInfoByInvestorid", body: body, token: token)
        )
        return envelope.data as? [String: Any]
    }

    /// 冷水钱包，`canUseMoney` 为分。
    static func queryCold(token: String, walletKey: String) async throws -> [String: Any]? {
        let envelope = try await ensure(
            call(
                method: "GET",
                path: "/paymentV1/app/wallet/find",
                params: [
                    ("investorId", investorId),
                    ("schoolId", schoolId),
                    ("walletKey", walletKey)
                ],
                token: token
            )
        )
        return envelope.data as? [String: Any]
    }

    // MARK: - 楼栋 / 房间 / 设备

    static func lowerAreas(token: String, areaId: String) async throws -> [LxtAreaNode] {
        let envelope = try await ensure(
            call(method: "GET", path: "/baseDict/site/getLowerAreas", params: [("areaId", areaId)], token: token)
        )
        return nodes(envelope.data)
    }

    static func rooms(token: String, areaId: String) async throws -> [LxtAreaNode] {
        let envelope = try await ensure(
            call(
                method: "GET",
                path: "/baseDict/site/getDormitoryOrPublicRoom",
                params: [("areaId", areaId)],
                token: token
            )
        )
        return nodes(envelope.data)
    }

    private static func nodes(_ data: Any?) -> [LxtAreaNode] {
        var array: [[String: Any]] = []
        if let list = data as? [[String: Any]] {
            array = list
        } else if let dict = data as? [String: Any] {
            for key in ["records", "list", "areas", "children", "data"] {
                if let list = dict[key] as? [[String: Any]] {
                    array = list
                    break
                }
            }
            if array.isEmpty, dict.string("id") ?? dict.string("areaId") != nil {
                array = [dict]
            }
        }
        return array.compactMap { item in
            guard let id = item.string("id") ?? item.string("areaId") ?? item.string("siteId") ?? item.string("roomId")
            else { return nil }
            let name = item.string("name") ?? item.string("areaName") ?? item.string("siteName")
                ?? item.string("roomName") ?? item.string("dormName") ?? item.string("dormitoryName")
                ?? "未命名"
            return LxtAreaNode(id: id, name: name, siteFlag: item.int("siteFlag") ?? item.int("siteflag") ?? 0)
        }
    }

    static func machines(token: String, siteId: String, siteFlag: Int, typeId: Int = 1) async throws -> [LxtMachine] {
        let body = jsonObject([
            ("siteId", siteId),
            ("siteFlag", siteFlag),
            ("typeId", typeId)
        ])
        let envelope = try await ensure(
            call(method: "POST", path: "/mgapp/machine/getMachineByLocation", body: body, token: token)
        )
        var array: [[String: Any]] = []
        if let list = envelope.data as? [[String: Any]] {
            array = list
        } else if let dict = envelope.data as? [String: Any] {
            array = (dict["records"] as? [[String: Any]])
                ?? (dict["list"] as? [[String: Any]])
                ?? [dict]
        }
        return array.compactMap { item in
            guard let machineId = item.string("machineId"), !machineId.isEmpty else { return nil }
            return LxtMachine(
                machineId: machineId,
                deviceMac: item.string("deviceMac") ?? "",
                deviceVer: item.string("deviceVer") ?? "",
                roomName: item.string("roominfo") ?? item.string("roomName") ?? "",
                siteId: item.string("siteId") ?? "",
                siteFlag: item.int("siteFlag") ?? item.int("siteflag") ?? item.int("flag") ?? 0
            )
        }
    }

    static func machineStatus(token: String, siteId: String, siteFlag: Int) async throws -> [LxtMachineStatus] {
        guard !siteId.isEmpty else { return [] }
        let envelope = try await ensure(
            call(
                method: "GET",
                path: "/bath/machine/status/getList",
                params: [
                    ("investorId", investorId),
                    ("schoolId", schoolId),
                    ("siteId", siteId),
                    ("siteFlag", "\(siteFlag)"),
                    ("typeId", "1")
                ],
                token: token
            )
        )
        var array: [[String: Any]] = []
        if let list = envelope.data as? [[String: Any]] {
            array = list
        } else if let dict = envelope.data as? [String: Any] {
            array = (dict["machineStatusList"] as? [[String: Any]])
                ?? (dict["records"] as? [[String: Any]])
                ?? (dict["list"] as? [[String: Any]])
                ?? []
        }
        return array.compactMap { item in
            guard let machineId = item.string("machineId") else { return nil }
            return LxtMachineStatus(machineId: machineId, status: item.int("curStatus") ?? item.int("status") ?? 0)
        }
    }

    static func isRunning(_ status: Int) -> Bool { status == 111 || status == 121 }

    // MARK: - 开阀

    static func channelWay(deviceVer: String) -> Int {
        let parts = deviceVer.components(separatedBy: ",")
        if parts.first?.trimmingCharacters(in: .whitespaces) == "00" { return 0 }
        let middle = (parts.count > 1 ? parts[1] : "").replacingOccurrences(of: " ", with: "").uppercased()
        return ["02", "03", "04", "05"].contains(middle) ? 2 : 4
    }

    static func openValve(token: String, machineId: String, deviceVer: String) async throws -> [String: Any]? {
        let channel = channelWay(deviceVer: deviceVer)
        guard channel != 0 else {
            throw LxtError(code: -2, message: "该设备不支持远程开阀，请现场操作")
        }
        let body = "{"
            + "\"typeId\":1,"
            + "\"channelWay\":\(channel),"
            + "\"machineId\":\(quoted(machineId)),"
            + "\"deviceVer\":\(quoted(deviceVer)),"
            + "\"machineInfoJson\":{\"lastPosMoney\":\"0000\",\"lastPosSerial\":\"0000\"}"
            + "}"
        let envelope = try await ensure(
            call(method: "POST", path: "/bath/valve/open", body: body, token: token)
        )
        return envelope.data as? [String: Any]
    }

    /// 返回 nil 表示服务端没给 status，界面按「未知」处理。
    static func valveStatus(
        token: String,
        machineId: String,
        communicationVal: String
    ) async throws -> Int? {
        let envelope = try await ensure(
            call(
                method: "GET",
                path: "/bath/valve/getStatus",
                params: [
                    ("typeId", "1"),
                    ("machineId", machineId),
                    ("modeValue", "1"),
                    ("communicationVal", communicationVal)
                ],
                token: token
            )
        )
        if let dict = envelope.data as? [String: Any] { return dict.int("status") }
        if let number = envelope.data as? NSNumber { return number.intValue }
        return nil
    }

    // MARK: - HTTP

    private struct Envelope {
        var code: Int
        var message: String
        var data: Any?
        var token: String?
    }

    private static func call(
        method: String,
        path: String,
        params: [(String, String)] = [],
        body: String? = nil,
        token: String? = nil
    ) async throws -> Envelope {
        let isGet = method == "GET"
        let query = params.map { "\($0.0)=\($0.1)" }.joined(separator: "&")
        let signBase = isGet ? query : (body ?? "{}")
        var urlText = host + path
        if isGet && !query.isEmpty { urlText += "?" + query }

        var request = URLRequest(url: URL(string: urlText)!)
        request.httpMethod = method
        for (key, value) in headers(token: token, isGet: isGet, signBase: signBase) {
            request.setValue(value, forHTTPHeaderField: key)
        }
        if !isGet {
            request.httpBody = (body ?? "{}").data(using: .utf8)
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        let text = String(data: data, encoding: .utf8) ?? ""
        guard let root = JSON.object(text), root.int("Code") != nil else {
            throw LxtError(code: -1, message: "服务器响应异常（\(http?.statusCode ?? 0)）")
        }
        return Envelope(
            code: root.int("Code") ?? -1,
            message: root.string("Message") ?? "",
            data: root["Data"],
            token: extractToken(http)
        )
    }

    private static func ensure(_ envelope: Envelope) throws -> Envelope {
        guard envelope.code == 0 else {
            let message = envelope.message.isEmpty ? "请求失败（\(envelope.code)）" : envelope.message
            throw LxtError(code: envelope.code, message: message)
        }
        return envelope
    }

    private static func headers(token: String?, isGet: Bool, signBase: String) -> [String: String] {
        var result: [String: String] = [
            "Content-Type": isGet ? "application/x-www-form-urlencoded" : "application/json",
            "X-Sign": md5(signSalt + signBase),
            "X-Ghost": xGhost(),
            "X-Sid": "2021011300001",
            "X-clientType": "4",
            "X-Product-Ver": "4.1.6.46#?1.13.23",
            "User-Agent": "okhttp/3.12.1"
        ]
        if let token, !token.isEmpty {
            result["x-token"] = "tokenInfo=\(token)"
            result["Cookie"] = "tokenInfo=\(token)"
        }
        return result
    }

    private static func extractToken(_ response: HTTPURLResponse?) -> String? {
        guard let response else { return nil }
        for (key, value) in response.allHeaderFields {
            let name = String(describing: key).lowercased()
            let text = String(describing: value)
            if name == "tokeninfo" {
                let token = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !token.isEmpty && !token.contains(";") { return token }
            }
            if name == "set-cookie" || name == "cookie" {
                for part in text.components(separatedBy: ";") {
                    let item = part.trimmingCharacters(in: .whitespaces)
                    guard item.lowercased().hasPrefix("tokeninfo=") else { continue }
                    if let token = item.components(separatedBy: "=").dropFirst().first,
                       !token.isEmpty {
                        return token
                    }
                }
            }
        }
        return nil
    }

    private static func md5(_ input: String) -> String {
        let digest = Insecure.MD5.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private static func xGhost() -> String {
        let millis = Int(Date().timeIntervalSince1970 * 1000)
        var hex = String(millis, radix: 16, uppercase: true)
        if hex.count > 12 { hex = String(hex.suffix(12)) }
        while hex.count < 12 { hex = "0" + hex }
        var sum = 0
        var xor = 0
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2, limitedBy: hex.endIndex) ?? hex.endIndex
            let chunk = String(hex[index..<next])
            if let byte = Int(chunk, radix: 16) {
                sum += byte
                xor ^= byte
            }
            index = next
        }
        return String(format: "%02X", sum % 256) + hex + String(format: "%02X", xor & 0xFF)
    }

    private static func quoted(_ value: String) -> String {
        var result = "\""
        for character in value {
            switch character {
            case "\"": result += "\\\""
            case "\\": result += "\\\\"
            case "\n": result += "\\n"
            case "\r": result += "\\r"
            case "\t": result += "\\t"
            default: result.append(character)
            }
        }
        return result + "\""
    }

    private static func jsonObject(_ pairs: [(String, Any?)]) -> String {
        var parts: [String] = []
        for (key, value) in pairs {
            let jsonValue: String
            switch value {
            case let text as String: jsonValue = quoted(text)
            case let number as Int: jsonValue = String(number)
            case let number as Double: jsonValue = String(number)
            case let flag as Bool: jsonValue = flag ? "true" : "false"
            case nil: jsonValue = "null"
            default: jsonValue = quoted(String(describing: value!))
            }
            parts.append("\(quoted(key)):\(jsonValue)")
        }
        return "{" + parts.joined(separator: ",") + "}"
    }
}
