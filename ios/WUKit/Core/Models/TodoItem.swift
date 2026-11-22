import Foundation

struct TodoItem: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var title: String
    var done: Bool = false
    var dayKey: String
    var createdAt: Date = Date()
}
