import Foundation

struct ScheduleSection: Identifiable, Codable, Hashable {
    var number: Int
    var start: String
    var end: String

    var id: Int { number }
}

struct Course: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var name: String
    var teacher: String = ""
    var room: String = ""
    /// 1 = 周一 … 7 = 周日
    var weekday: Int
    var startSection: Int
    var endSection: Int
    /// 0…7，对应调色板
    var colorIndex: Int = 0

    var span: Int { max(1, endSection - startSection + 1) }
}
