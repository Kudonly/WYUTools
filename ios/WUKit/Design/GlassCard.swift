import SwiftUI

/// 柔光玻璃卡。iOS 直接用系统 Material 采样背后内容，
/// 不需要 Android 那套自写 shader（折射/反射交给系统）。
struct GlassCard<Content: View>: View {
    var radius: CGFloat
    var padding: CGFloat
    var surface: Material
    var content: Content

    init(
        radius: CGFloat = WK.cardRadius,
        padding: CGFloat = WK.cardPadding,
        surface: Material = .ultraThinMaterial,
        @ViewBuilder content: () -> Content
    ) {
        self.radius = radius
        self.padding = padding
        self.surface = surface
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(surface, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.20), lineWidth: 0.5)
            }
            .shadow(color: Color.black.opacity(0.06), radius: 18, x: 0, y: 8)
    }
}

/// 可点击的卡片，带轻微下沉反馈。
struct GlassCardButton<Content: View>: View {
    var radius: CGFloat = WK.cardRadius
    var padding: CGFloat = WK.cardPadding
    var action: () -> Void
    var content: Content

    init(
        radius: CGFloat = WK.cardRadius,
        padding: CGFloat = WK.cardPadding,
        action: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.radius = radius
        self.padding = padding
        self.action = action
        self.content = content()
    }

    var body: some View {
        Button(action: action) {
            GlassCard(radius: radius, padding: padding) {
                content
            }
        }
        .buttonStyle(CardPressStyle())
    }
}

struct CardPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .animation(.snappy(duration: 0.16), value: configuration.isPressed)
    }
}
