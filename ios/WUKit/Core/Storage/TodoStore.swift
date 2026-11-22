import Foundation
import Observation

/// 待办：按天存一份 JSON 到 UserDefaults，跨天自动换清单。
@Observable
final class TodoStore {
    static let shared = TodoStore()

    private static let storageKey = "wukit.todos"

    private(set) var items: [TodoItem] = []
    private(set) var dayKey: String = TodoStore.todayKey()

    init() {
        load()
    }

    var doneCount: Int {
        items.filter(\.done).count
    }

    var remainingCount: Int {
        items.count - doneCount
    }

    func add(_ title: String) {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        rolloverIfNeeded()
        items.append(TodoItem(title: trimmed, dayKey: dayKey))
        save()
    }

    func toggle(_ item: TodoItem) {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        items[index].done.toggle()
        save()
    }

    func remove(_ item: TodoItem) {
        items.removeAll { $0.id == item.id }
        save()
    }

    // MARK: - Storage

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: Self.storageKey),
              let decoded = try? JSONDecoder().decode([TodoItem].self, from: data)
        else { return }
        items = decoded
        rolloverIfNeeded()
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(items) else { return }
        UserDefaults.standard.set(data, forKey: Self.storageKey)
    }

    private func rolloverIfNeeded() {
        let today = Self.todayKey()
        guard dayKey != today else { return }
        dayKey = today
        items = []
        save()
    }

    private static func todayKey() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
