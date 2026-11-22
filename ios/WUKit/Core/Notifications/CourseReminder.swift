import Foundation
import UserNotifications

/// 上课提醒：把课表里的课按周重复排成本地通知。
/// 只做本机能做的事，不联网；权限被拒就静默关掉开关。
enum CourseReminder {
    static let leadOptions = [5, 10, 15, 20, 30]

    static func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        do {
            return try await center.requestAuthorization(options: [.alert, .sound])
        } catch {
            return false
        }
    }

    /// 按当前设置重排全部提醒。关闭开关或没有课时，清空待发通知。
    static func sync() {
        let settings = AppSettings.shared
        let store = ScheduleStore.shared
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()

        guard settings.reminderOn, !store.courses.isEmpty else { return }

        for course in store.courses {
            guard let section = store.sections.first(where: { $0.number == course.startSection }),
                  let start = minutes(from: section.start)
            else { continue }

            var total = start - settings.reminderLead
            if total < 0 { total += 24 * 60 }

            let content = UNMutableNotificationContent()
            content.title = course.name
            var body = "第 \(course.startSection) 节 \(section.start) 开始"
            if !course.room.isEmpty {
                body += " · \(course.room)"
            }
            content.body = body
            content.sound = .default

            var components = DateComponents()
            // Calendar 的 weekday 是 1 = 周日，课表是 1 = 周一。
            components.weekday = course.weekday % 7 + 1
            components.hour = total / 60
            components.minute = total % 60

            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            let request = UNNotificationRequest(
                identifier: "wk.course.\(course.id.uuidString)",
                content: content,
                trigger: trigger
            )
            center.add(request)
        }
    }

    private static func minutes(from text: String) -> Int? {
        let parts = text.split(separator: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]) else { return nil }
        return hour * 60 + minute
    }
}
