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

    /// 与 Android 的 WyuTool.icon 一一对应（Material Icons Outlined）。
    var glyph: WKGlyph {
        switch self {
        case .today: return .today
        case .schedule: return .calendarMonth
        case .lxt: return .waterDrop
        case .elec: return .bolt
        case .gym: return .basketball
        case .exam: return .assignment
        case .grades: return .school
        case .eval: return .rateReview
        case .physical: return .fitnessCenter
        }
    }

    /// 安卓更多页给每个工具定的底色（iOS 系统色系）。
    var iconHex: UInt32 {
        switch self {
        case .today: return 0xFF6B00
        case .schedule: return 0x0A84FF
        case .lxt: return 0x30B0C7
        case .elec: return 0xFF9F0A
        case .gym: return 0x30D158
        case .exam: return 0xBF5AF2
        case .grades: return 0x5E5CE6
        case .eval: return 0xFF375F
        case .physical: return 0x00C7BE
        }
    }

    var tint: Color { Color(hex: iconHex) }

    /// 图标方块的渐变：左上角提亮 22%，右下角回到本色 —— 与安卓同一条 Brush。
    var iconGradient: LinearGradient {
        LinearGradient(
            colors: [Color(hex: iconHex.lightened(0.22)), tint],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    /// 常用页默认铺的卡片。
    static let defaultCommon: [WKTool] = [.today, .schedule, .lxt, .elec, .gym, .exam]
}
