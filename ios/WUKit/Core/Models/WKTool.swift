import SwiftUI

/// 工具清单，与 Android 的 WyuTool 一一对应。
enum WKTool: String, CaseIterable, Identifiable, Hashable {
    case today
    case schedule
    case lxt
    case elec
    case gym
    case exam
    case grades
    case eval
    case physical

    var id: String { rawValue }

    var title: String {
        switch self {
        case .today: return "今日"
        case .schedule: return "课表"
        case .lxt: return "洗澡"
        case .elec: return "电费"
        case .gym: return "体育馆预约"
        case .exam: return "考试"
        case .grades: return "成绩"
        case .eval: return "教评"
        case .physical: return "体测"
        }
    }

    var summary: String {
        switch self {
        case .today: return "今日待办 · 今天的课"
        case .schedule: return "本周课程 · 今日安排"
        case .lxt: return "热水余额 · 开阀用水"
        case .elec: return "用电查询 · 扫码充值"
        case .gym: return "场地查询 · 在线预约"
        case .exam: return "考试安排查询"
        case .grades: return "学期成绩 · 单科分析"
        case .eval: return "结课评价 · 评价明细"
        case .physical: return "体测总分 · 各项成绩"
        }
    }

    var symbol: String {
        switch self {
        case .today: return "checklist"
        case .schedule: return "calendar"
        case .lxt: return "drop"
        case .elec: return "bolt"
        case .gym: return "basketball"
        case .exam: return "doc.text"
        case .grades: return "graduationcap"
        case .eval: return "checkmark.seal"
        case .physical: return "figure.run"
        }
    }

    var tint: Color {
        switch self {
        case .today: return WKPalette.today
        case .schedule: return WKPalette.schedule
        case .lxt: return WKPalette.lxt
        case .elec: return WKPalette.elec
        case .gym: return WKPalette.gym
        case .exam: return WKPalette.exam
        case .grades: return WKPalette.grades
        case .eval: return WKPalette.eval
        case .physical: return WKPalette.physical
        }
    }

    /// 常用页默认铺的卡片。
    static let defaultCommon: [WKTool] = [.today, .schedule, .lxt, .elec, .gym, .exam]
}
