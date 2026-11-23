import SwiftUI
import UIKit
import Observation

/// 深浅色模式，与 Android 的 WyuThemeMode 一一对应。
enum WKThemeMode: String, CaseIterable, Identifiable {
    case dark
    case light
    case system

    var id: String { rawValue }

    var label: String {
        switch self {
        case .dark: return "深色"
        case .light: return "浅色"
        case .system: return "跟随系统"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }
}

/// 壁纸是四套渐变加一张自定义照片，色值直接取自 Android 端。
enum WKWallpaperKind: String, CaseIterable, Identifiable {
    case mist
    case jade
    case dusk
    case aurora
    case custom

    var id: String { rawValue }

    var label: String {
        switch self {
        case .mist: return "晨雾"
        case .jade: return "青岚"
        case .dusk: return "晚霞"
        case .aurora: return "极光"
        case .custom: return "自定义"
        }
    }

    var isCustom: Bool { self == .custom }

    /// 三段渐变；自定义壁纸返回 nil，由图片自己铺。
    func stops(dark: Bool) -> [Color]? {
        switch self {
        case .custom:
            return nil
        case .mist:
            return dark
                ? [Color(hex: 0x24324A), Color(hex: 0x1D2737), Color(hex: 0x181E2A)]
                : [Color(hex: 0xC9DDF4), Color(hex: 0xE2EDF9), Color(hex: 0xF4F7FC)]
        case .jade:
            return dark
                ? [Color(hex: 0x1E352C), Color(hex: 0x192822), Color(hex: 0x16201C)]
                : [Color(hex: 0xCBE8DB), Color(hex: 0xE0F1E9), Color(hex: 0xF0F7F3)]
        case .dusk:
            return dark
                ? [Color(hex: 0x3B2732), Color(hex: 0x2B2029), Color(hex: 0x221C22)]
                : [Color(hex: 0xF6D2C2), Color(hex: 0xF7E4DA), Color(hex: 0xFBF1F3)]
        case .aurora:
            return dark
                ? [Color(hex: 0x2F2650), Color(hex: 0x23203A), Color(hex: 0x1C1A2C)]
                : [Color(hex: 0xDCD2F6), Color(hex: 0xE4E8FA), Color(hex: 0xF2EEFB)]
        }
    }
}

/// 卡片材质。Android 的 默认 / 柔光玻璃 / 莫奈 在这里对应 磨砂 / 液态 / 实色。
enum WKCardStyle: String, CaseIterable, Identifiable {
    case frosted
    case liquid
    case solid

    var id: String { rawValue }

    var label: String {
        switch self {
        case .frosted: return "磨砂玻璃"
        case .liquid: return "液态玻璃"
        case .solid: return "实色"
        }
    }

    var summary: String {
        switch self {
        case .frosted: return "系统材质模糊，透出壁纸底色"
        case .liquid: return "系统液态玻璃，带折射、高光与触感反馈"
        case .solid: return "半透明实色卡片，不叠玻璃"
        }
    }
}

/// 强调色：跟随系统蓝，也可以换成别的。
enum WKAccent: String, CaseIterable, Identifiable {
    case blue
    case indigo
    case teal
    case green
    case orange
    case pink
    case graphite

    var id: String { rawValue }

    var label: String {
        switch self {
        case .blue: return "蓝色"
        case .indigo: return "靛蓝"
        case .teal: return "青色"
        case .green: return "绿色"
        case .orange: return "橙色"
        case .pink: return "粉色"
        case .graphite: return "石墨"
        }
    }

    var color: Color {
        switch self {
        case .blue: return Color(hex: 0x2F6BFF)
        case .indigo: return Color(hex: 0x6A5AE0)
        case .teal: return Color(hex: 0x1F9C96)
        case .green: return Color(hex: 0x2E9E5B)
        case .orange: return Color(hex: 0xE08616)
        case .pink: return Color(hex: 0xD94F8E)
        case .graphite: return Color(hex: 0x5B6470)
        }
    }
}

/// 全局外观与偏好。和 Android 的 WyuAppearance + WyuToolLayoutStore 对齐，
/// 只是这里用 @Observable + UserDefaults，SwiftUI 直接读。
@Observable
final class AppSettings {
    static let shared = AppSettings()

    private enum Key {
        static let themeMode = "wk.appearance.themeMode"
        static let wallpaper = "wk.appearance.wallpaper"
        static let cardStyle = "wk.appearance.cardStyle"
        static let accent = "wk.appearance.accent"
        static let glassStrength = "wk.appearance.glassStrength"
        static let wallpaperBrightness = "wk.appearance.wallpaperBrightness"
        static let common = "wk.layout.common"
        static let more = "wk.layout.more"
        static let showWeekend = "wk.schedule.showWeekend"
        static let doublePeriod = "wk.schedule.doublePeriod"
        static let demoMode = "wk.profile.demoMode"
        static let reminderOn = "wk.reminder.on"
        static let reminderLead = "wk.reminder.lead"
    }

    private static let defaultCommon: [WKTool] = [.schedule, .today, .lxt, .elec, .gym]
    private static let defaultMore: [WKTool] = [.exam, .grades, .eval, .physical]

    private let defaults = UserDefaults.standard

    var themeMode: WKThemeMode {
        didSet { defaults.set(themeMode.rawValue, forKey: Key.themeMode) }
    }

    var wallpaper: WKWallpaperKind {
        didSet { defaults.set(wallpaper.rawValue, forKey: Key.wallpaper) }
    }

    var cardStyle: WKCardStyle {
        didSet { defaults.set(cardStyle.rawValue, forKey: Key.cardStyle) }
    }

    var accent: WKAccent {
        didSet { defaults.set(accent.rawValue, forKey: Key.accent) }
    }

    /// 液态玻璃的着色强度（0…1）。0 是纯系统玻璃，越大越吃强调色。
    var glassStrength: Double {
        didSet { defaults.set(glassStrength, forKey: Key.glassStrength) }
    }

    /// 自定义壁纸亮度（0.5…1），深色浅色各存一份，这里只存当前模式在读的那份。
    var wallpaperBrightness: Double {
        didSet { defaults.set(wallpaperBrightness, forKey: Key.wallpaperBrightness) }
    }

    var showWeekend: Bool {
        didSet { defaults.set(showWeekend, forKey: Key.showWeekend) }
    }

    var doublePeriod: Bool {
        didSet { defaults.set(doublePeriod, forKey: Key.doublePeriod) }
    }

    var demoMode: Bool {
        didSet { defaults.set(demoMode, forKey: Key.demoMode) }
    }

    var reminderOn: Bool {
        didSet { defaults.set(reminderOn, forKey: Key.reminderOn) }
    }

    var reminderLead: Int {
        didSet { defaults.set(reminderLead, forKey: Key.reminderLead) }
    }

    /// 常用页卡片，顺序就是显示顺序。
    var commonTools: [WKTool] {
        didSet { persistLayout() }
    }

    /// 更多页图标，顺序就是网格顺序。
    var moreTools: [WKTool] {
        didSet { persistLayout() }
    }

    /// 自定义壁纸（已缩放到长边 2560 以内）。
    private(set) var customWallpaper: UIImage?

    var hasCustomWallpaper: Bool { customWallpaper != nil }

    init() {
        let d = UserDefaults.standard
        themeMode = WKThemeMode(rawValue: d.string(forKey: Key.themeMode) ?? "") ?? .system
        wallpaper = WKWallpaperKind(rawValue: d.string(forKey: Key.wallpaper) ?? "") ?? .mist
        cardStyle = WKCardStyle(rawValue: d.string(forKey: Key.cardStyle) ?? "") ?? .liquid
        accent = WKAccent(rawValue: d.string(forKey: Key.accent) ?? "") ?? .blue
        glassStrength = d.object(forKey: Key.glassStrength) as? Double ?? 0.55
        wallpaperBrightness = d.object(forKey: Key.wallpaperBrightness) as? Double ?? 1.0
        showWeekend = d.object(forKey: Key.showWeekend) as? Bool ?? true
        doublePeriod = d.object(forKey: Key.doublePeriod) as? Bool ?? true
        demoMode = d.object(forKey: Key.demoMode) as? Bool ?? true
        reminderOn = d.object(forKey: Key.reminderOn) as? Bool ?? false
        reminderLead = d.object(forKey: Key.reminderLead) as? Int ?? 15

        let storedCommon = (d.array(forKey: Key.common) as? [String])?.compactMap(WKTool.init(rawValue:))
        let storedMore = (d.array(forKey: Key.more) as? [String])?.compactMap(WKTool.init(rawValue:))
        if let storedCommon, let storedMore, (storedCommon + storedMore).count == WKTool.allCases.count {
            commonTools = storedCommon
            moreTools = storedMore
        } else {
            commonTools = Self.defaultCommon
            moreTools = Self.defaultMore
        }

        if let data = try? Data(contentsOf: Self.customWallpaperURL) {
            customWallpaper = UIImage(data: data)
        }
        if wallpaper == .custom && customWallpaper == nil {
            wallpaper = .mist
        }
    }

    // MARK: - 壁纸

    private static var customWallpaperURL: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("custom-wallpaper.jpg")
    }

    func setCustomWallpaper(_ image: UIImage) {
        let scaled = image.wkScaled(maxSide: 2560)
        guard let data = scaled.jpegData(compressionQuality: 0.88) else { return }
        try? data.write(to: Self.customWallpaperURL, options: .atomic)
        customWallpaper = scaled
        wallpaper = .custom
    }

    func clearCustomWallpaper() {
        try? FileManager.default.removeItem(at: Self.customWallpaperURL)
        customWallpaper = nil
        if wallpaper == .custom { wallpaper = .mist }
    }

    // MARK: - 工具布局

    private func persistLayout() {
        defaults.set(commonTools.map(\.rawValue), forKey: Key.common)
        defaults.set(moreTools.map(\.rawValue), forKey: Key.more)
    }

    func setInCommon(_ tool: WKTool, _ inCommon: Bool) {
        if inCommon {
            guard !commonTools.contains(tool) else { return }
            commonTools.append(tool)
            moreTools.removeAll { $0 == tool }
        } else {
            guard !moreTools.contains(tool) else { return }
            moreTools.append(tool)
            commonTools.removeAll { $0 == tool }
        }
    }

    func moveCommon(from source: IndexSet, to destination: Int) {
        commonTools.move(fromOffsets: source, toOffset: destination)
    }

    func moveMore(from source: IndexSet, to destination: Int) {
        moreTools.move(fromOffsets: source, toOffset: destination)
    }

    /// 更多页拖动换位：把 from 挪到 before 的位置。
    func moveInMore(_ from: WKTool, before to: WKTool) {
        guard from != to,
              let fromIndex = moreTools.firstIndex(of: from),
              let toIndex = moreTools.firstIndex(of: to)
        else { return }
        var next = moreTools
        next.remove(at: fromIndex)
        let target = fromIndex < toIndex ? toIndex - 1 : toIndex
        next.insert(from, at: max(0, min(next.count, target)))
        moreTools = next
    }

    // MARK: - 其它

    var accentColor: Color { accent.color }
}

extension UIImage {
    /// 长边压到 maxSide 以内，省内存也省磁盘。
    func wkScaled(maxSide: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxSide, longest > 0 else { return self }
        let scale = maxSide / longest
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}
