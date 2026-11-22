import SwiftUI
import PhotosUI

/// 设置：壁纸、外观、主题、课表、卡片管理、诊断、关于。
/// 顺序与 Android 设置页一致，控件换成原生 SwiftUI。
struct SettingsView: View {
    @Bindable private var settings = AppSettings.shared
    @Environment(\.colorScheme) private var scheme

    @State private var photoItem: PhotosPickerItem?
    @State private var showCommonCards = false

    /// PhotosPickerItem 本身不一定 Equatable，用标识符当变化信号更稳。
    private var photoKey: String { photoItem?.itemIdentifier ?? "" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                WKSectionTitle(text: "壁纸")
                wallpaperPicker
                if settings.wallpaper == .custom && settings.hasCustomWallpaper {
                    brightnessCard
                }

                WKSectionTitle(text: "外观")
                    .padding(.top, 4)
                appearanceCard

                WKSectionTitle(text: "主题")
                    .padding(.top, 4)
                themeCard

                WKSectionTitle(text: "课表")
                    .padding(.top, 4)
                scheduleCard

                WKSectionTitle(text: "卡片与工具")
                    .padding(.top, 4)
                commonCardsEntry

                WKSectionTitle(text: "诊断")
                    .padding(.top, 4)
                networkEntry

                WKSectionTitle(text: "关于")
                    .padding(.top, 4)
                aboutCard

                Color.clear.frame(height: 28)
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 4)
        }
        .background(WKWallpaperBackground())
        .scrollIndicators(.hidden)
        .navigationTitle("设置")
        .sheet(isPresented: $showCommonCards) {
            CommonCardsView()
        }
        .onChange(of: photoKey) { _, _ in
            guard let item = photoItem else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    await MainActor.run {
                        settings.setCustomWallpaper(image)
                    }
                }
                await MainActor.run { photoItem = nil }
            }
        }
        .onChange(of: settings.reminderOn) { _, enabled in
            if enabled {
                Task {
                    let granted = await CourseReminder.requestAuthorization()
                    await MainActor.run {
                        if granted {
                            CourseReminder.sync()
                        } else {
                            settings.reminderOn = false
                        }
                    }
                }
            } else {
                CourseReminder.sync()
            }
        }
        .onChange(of: settings.reminderLead) { _, _ in
            CourseReminder.sync()
        }
    }

    // MARK: - 壁纸

    private var wallpaperPicker: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 12) {
                ForEach(WKWallpaperKind.allCases) { kind in
                    if kind.isCustom {
                        PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                            swatch(kind)
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button {
                            withAnimation(.snappy(duration: 0.2)) {
                                settings.wallpaper = kind
                            }
                        } label: {
                            swatch(kind)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 4)
        }
        .scrollIndicators(.hidden)
    }

    private func swatch(_ kind: WKWallpaperKind) -> some View {
        let selected = settings.wallpaper == kind
        return VStack(spacing: 6) {
            ZStack {
                if kind.isCustom, let image = settings.customWallpaper {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else if kind.isCustom {
                    Color.primary.opacity(0.06)
                    Image(systemName: "photo.badge.plus")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(.secondary)
                } else {
                    LinearGradient(
                        colors: kind.stops(dark: scheme == .dark) ?? [],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                }
            }
            .frame(width: 62, height: 86)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(
                        selected ? settings.accentColor : Color.white.opacity(0.22),
                        lineWidth: selected ? 2.5 : 0.8
                    )
            }

            Text(kind.label)
                .font(.system(size: 11.5, weight: selected ? .semibold : .regular))
                .foregroundStyle(selected ? .primary : .secondary)
        }
        .contentShape(Rectangle())
    }

    private var brightnessCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("壁纸亮度")
                        .font(.system(size: 16, weight: .semibold))
                    Spacer()
                    Text("\(Int(settings.wallpaperBrightness * 100))%")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
                Slider(value: $settings.wallpaperBrightness, in: 0.5...1)
                    .tint(settings.accentColor)
                Text("调暗后玻璃更透，深色模式尤其明显。")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - 外观

    private var appearanceCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("深浅色模式")
                    .font(.system(size: 16, weight: .semibold))
                Text("选择应用的显示模式")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .padding(.top, -6)

                HStack(spacing: 8) {
                    ForEach(WKThemeMode.allCases) { mode in
                        themeChip(mode)
                    }
                }
            }
        }
    }

    private func themeChip(_ mode: WKThemeMode) -> some View {
        let selected = settings.themeMode == mode
        return Button {
            withAnimation(.snappy(duration: 0.2)) {
                settings.themeMode = mode
            }
        } label: {
            Text(mode.label)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(selected ? Color.white : Color.primary)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(
                    selected ? AnyShapeStyle(settings.accentColor) : AnyShapeStyle(Color.primary.opacity(0.06)),
                    in: Capsule()
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - 主题

    private var themeCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(WKCardStyle.allCases) { style in
                    Button {
                        withAnimation(.snappy(duration: 0.22)) {
                            settings.cardStyle = style
                        }
                    } label: {
                        themeStyleRow(style)
                    }
                    .buttonStyle(.plain)

                    if style != WKCardStyle.allCases.last {
                        WKDivider()
                    }
                }

                if settings.cardStyle == .liquid {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("着色强度")
                                .font(.system(size: 14))
                            Spacer()
                            Text("\(Int(settings.glassStrength * 100))%")
                                .font(.system(size: 13))
                                .foregroundStyle(.secondary)
                        }
                        Slider(value: $settings.glassStrength, in: 0...1)
                            .tint(settings.accentColor)
                        Text("跟着系统液态玻璃的高光与折射，数值只改强调色的比例。")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.top, 4)
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("强调色")
                        .font(.system(size: 14))
                        .padding(.top, 4)
                    HStack(spacing: 12) {
                        ForEach(WKAccent.allCases) { accent in
                            accentDot(accent)
                        }
                        Spacer(minLength: 0)
                    }
                }
            }
        }
    }

    private func themeStyleRow(_ style: WKCardStyle) -> some View {
        HStack(spacing: 12) {
            stylePreview(style)
            VStack(alignment: .leading, spacing: 2) {
                Text(style.label)
                    .font(.system(size: 16, weight: .semibold))
                Text(style.summary)
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            if settings.cardStyle == style {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(settings.accentColor)
            }
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private func stylePreview(_ style: WKCardStyle) -> some View {
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        ZStack {
            LinearGradient(
                colors: [Color(hex: 0x8FB6F0), Color(hex: 0xE3B6C8)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            switch style {
            case .frosted:
                Color.clear.background(.ultraThinMaterial)
            case .liquid:
                Color.clear.background(.thinMaterial)
                shape.strokeBorder(Color.white.opacity(0.6), lineWidth: 1)
            case .solid:
                settings.accentColor.opacity(0.28)
            }
        }
        .frame(width: 48, height: 48)
        .clipShape(shape)
        .overlay { shape.strokeBorder(Color.white.opacity(0.25), lineWidth: 0.5) }
    }

    private func accentDot(_ accent: WKAccent) -> some View {
        Button {
            withAnimation(.snappy(duration: 0.2)) {
                settings.accent = accent
            }
        } label: {
            ZStack {
                Circle()
                    .fill(accent.color)
                    .frame(width: 28, height: 28)
                if settings.accent == accent {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.white)
                }
            }
            .frame(width: 32, height: 32)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accent.label)
    }

    // MARK: - 课表

    private var scheduleCard: some View {
        GlassCard {
            VStack(spacing: 14) {
                Toggle(isOn: $settings.showWeekend) {
                    rowLabel("显示周六日", "课表网格是否带周末两列")
                }
                .tint(settings.accentColor)

                WKDivider()

                Toggle(isOn: $settings.doublePeriod) {
                    rowLabel("连堂课合并显示", "两节连上时按加长的一格画")
                }
                .tint(settings.accentColor)

                WKDivider()

                Toggle(isOn: $settings.reminderOn) {
                    rowLabel("上课提醒", "按课程开始时间发本地通知")
                }
                .tint(settings.accentColor)

                if settings.reminderOn {
                    HStack {
                        Text("提前")
                            .font(.system(size: 14))
                        Spacer()
                        Picker("提前", selection: $settings.reminderLead) {
                            ForEach(CourseReminder.leadOptions, id: \.self) { value in
                                Text("\(value) 分钟").tag(value)
                            }
                        }
                        .pickerStyle(.menu)
                        .tint(settings.accentColor)
                    }
                }

                WKDivider()

                Toggle(isOn: $settings.demoMode) {
                    rowLabel("演示模式", "用预设资料替换「我」页面的个人信息")
                }
                .tint(settings.accentColor)
            }
        }
    }

    private func rowLabel(_ title: String, _ summary: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 16, weight: .medium))
            Text(summary)
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - 卡片与工具

    private var commonCardsEntry: some View {
        GlassCardButton(action: { showCommonCards = true }) {
            WKSettingRow(
                title: "常用卡片",
                summary: "决定常用页放哪几张卡、以什么顺序排",
                value: "\(settings.commonTools.count) 张",
                showsChevron: true
            )
        }
    }

    // MARK: - 诊断

    private var networkEntry: some View {
        NavigationLink {
            NetworkCheckView()
        } label: {
            GlassCard {
                WKSettingRow(
                    title: "接口连通性检测",
                    summary: "逐个探测五个后端域，确认校园网与 UA 伪装",
                    showsChevron: true
                )
            }
        }
        .buttonStyle(WKPressStyle())
    }

    // MARK: - 关于

    private var aboutCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                WKSettingRow(title: "版本", value: MeView.version)

                WKDivider()

                VStack(alignment: .leading, spacing: 4) {
                    Text("原生 SwiftUI 复刻")
                        .font(.system(size: 14, weight: .medium))
                    Text("界面按 iOS 26 液态玻璃设计，数据与接口逐步和 Android 版对齐。")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                WKDivider()

                Link(destination: URL(string: "https://github.com/Kudonly/WYUTools")!) {
                    HStack {
                        Text("GitHub 仓库")
                            .font(.system(size: 14, weight: .medium))
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

#Preview {
    NavigationStack {
        SettingsView()
    }
    .environment(AppSettings.shared)
}
