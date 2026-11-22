import SwiftUI

@main
struct WUKitApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .onChange(of: scenePhase) { _, phase in
            // 回到前台时按最新课表与设置重排提醒，跨天、改课都能跟上。
            if phase == .active {
                CourseReminder.sync()
            }
        }
    }
}
