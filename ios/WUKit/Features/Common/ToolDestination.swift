import SwiftUI

/// 工具入口 → 具体页面。九个工具都已经接上真实数据。
struct ToolDestination: View {
    let tool: WKTool

    var body: some View {
        switch tool {
        case .today:
            TodayView()
        case .schedule:
            ScheduleView()
        case .lxt:
            LxtView()
        case .elec:
            ElecView()
        case .gym:
            GymView()
        case .exam:
            ExamView()
        case .grades:
            GradesView()
        case .eval:
            EvalView()
        case .physical:
            PhysicalView()
        }
    }
}

#Preview {
    NavigationStack {
        ToolDestination(tool: .grades)
    }
    .environment(AppSettings.shared)
}
