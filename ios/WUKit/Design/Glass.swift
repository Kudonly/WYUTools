import SwiftUI

/// 材质核心。iOS 26 起直接交给系统的 Liquid Glass（`glassEffect`），
/// 老系统退回 Material，保证同一套调用点都能跑。
extension View {
    @ViewBuilder
    func wkSurface<S: InsettableShape>(
        _ shape: S,
        style: WKCardStyle,
        tint: Color? = nil,
        strength: Double = 0.5,
        interactive: Bool = false
    ) -> some View {
        switch style {
        case .frosted:
            self
                .background(.ultraThinMaterial, in: shape)
                .overlay { shape.strokeBorder(Color.white.opacity(0.16), lineWidth: 0.5) }
                .shadow(color: Color.black.opacity(0.10), radius: 18, x: 0, y: 8)
        case .solid:
            self
                .background(shape.fill(solidFill(tint: tint, strength: strength)))
                .background(.regularMaterial, in: shape)
                .overlay { shape.strokeBorder(Color.white.opacity(0.14), lineWidth: 0.5) }
                .shadow(color: Color.black.opacity(0.08), radius: 16, x: 0, y: 6)
        case .liquid:
            liquid(shape, tint: tint, strength: strength, interactive: interactive)
        }
    }

    private func solidFill(tint: Color?, strength: Double) -> Color {
        let base = tint ?? Color.primary
        let alpha = 0.10 + strength.clamped(to: 0...1) * 0.16
        return base.opacity(alpha)
    }

    private func glassTint(_ tint: Color?, _ strength: Double) -> Color? {
        guard let tint, strength > 0.01 else { return nil }
        return tint.opacity(0.10 + strength.clamped(to: 0...1) * 0.22)
    }

    @ViewBuilder
    private func liquid<S: InsettableShape>(
        _ shape: S,
        tint: Color?,
        strength: Double,
        interactive: Bool
    ) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self.glassEffect(
                .regular
                    .tint(glassTint(tint, strength))
                    .interactive(interactive),
                in: shape
            )
        } else {
            materialFallback(shape)
        }
        #else
        materialFallback(shape)
        #endif
    }

    private func materialFallback<S: InsettableShape>(_ shape: S) -> some View {
        self
            .background(.ultraThinMaterial, in: shape)
            .overlay {
                shape
                    .strokeBorder(
                        LinearGradient(
                            colors: [Color.white.opacity(0.55), Color.white.opacity(0.08)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 0.8
                    )
            }
            .shadow(color: Color.black.opacity(0.12), radius: 20, x: 0, y: 10)
    }
}

/// 内容卡片：圆角 28、内边距 18，和 Android 卡片同尺寸。
struct GlassCard<Content: View>: View {
    @Environment(AppSettings.self) private var settings

    private let radius: CGFloat
    private let padding: CGFloat
    private let tint: Color?
    private let style: WKCardStyle?
    private let content: Content

    init(
        radius: CGFloat = WK.cardRadius,
        padding: CGFloat = WK.cardPadding,
        tint: Color? = nil,
        style: WKCardStyle? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.radius = radius
        self.padding = padding
        self.tint = tint
        self.style = style
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .wkSurface(
                RoundedRectangle(cornerRadius: radius, style: .continuous),
                style: style ?? settings.cardStyle,
                tint: tint ?? settings.glassTint,
                strength: settings.glassStrength,
                interactive: true
            )
    }
}

/// 按压反馈：轻微下沉 + 变淡，liquid glass 的 interactive 会自己加高光。
struct WKPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.982 : 1)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .animation(.snappy(duration: 0.16), value: configuration.isPressed)
    }
}

/// 可点击的玻璃卡（内部用 NavigationLink 时传 plain 样式即可）。
struct GlassCardButton<Content: View>: View {
    private let radius: CGFloat
    private let padding: CGFloat
    private let tint: Color?
    private let action: () -> Void
    private let content: Content

    init(
        radius: CGFloat = WK.cardRadius,
        padding: CGFloat = WK.cardPadding,
        tint: Color? = nil,
        action: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.radius = radius
        self.padding = padding
        self.tint = tint
        self.action = action
        self.content = content()
    }

    var body: some View {
        Button(action: action) {
            GlassCard(radius: radius, padding: padding, tint: tint) {
                content
            }
        }
        .buttonStyle(WKPressStyle())
    }
}

/// 设置页的小标题（Android 的 SmallTitle）。
struct WKSectionTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, WK.sectionTitlePadding)
            .padding(.top, 2)
    }
}

/// 卡片里的一行「标题 + 说明 + 右侧值/箭头」，设置页反复用。
struct WKSettingRow: View {
    let title: String
    var summary: String? = nil
    var value: String? = nil
    var showsChevron = false

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.primary)
                if let summary {
                    Text(summary)
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            if let value {
                Text(value)
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)
            }
            if showsChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(WKInk.tertiary)
            }
        }
        .contentShape(Rectangle())
    }
}

/// 分隔线，缩进对齐文字。
struct WKDivider: View {
    var body: some View {
        Rectangle()
            .fill(Color.primary.opacity(0.08))
            .frame(height: 0.5)
    }
}
