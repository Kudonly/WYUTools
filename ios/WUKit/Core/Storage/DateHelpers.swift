import Foundation

extension DateFormatter {
    static var wkToday: DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M 月 d 日 EEEE"
        return formatter
    }
}

enum WKDate {
    /// 周一 = 1 … 周日 = 7。
    static var todayWeekday: Int {
        let weekday = Calendar.current.component(.weekday, from: Date())
        return weekday == 1 ? 7 : weekday - 1
    }

    static func weekdayTitle(_ weekday: Int) -> String {
        let names = ["一", "二", "三", "四", "五", "六", "日"]
        guard weekday >= 1, weekday <= 7 else { return "" }
        return names[weekday - 1]
    }

    /// "2026-11-23"
    static var todayKey: String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
