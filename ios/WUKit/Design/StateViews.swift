import SwiftUI

/// 加载 / 错误 / 空状态共用一张卡，避免每个页面各写一套。
struct WKStatusCard: View {
    enum Kind: Equatable {
        case loading
        case error
        case empty

        var symbol: String {
            switch self {
            case .loading: return "hourglass"
            case .error: return "exclamationmark.triangle"
            case .empty: return "tray"
            }
        }
    }

    let kind: Kind
    let message: String
    var retry: (() -> Void)? = nil

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    if kind == .loading {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: kind.symbol)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(kind == .error ? Color.orange : Color.secondary)
                    }
                    Text(message)
                        .font(.system(size: 14))
                        .foregroundStyle(kind == .error ? .primary : .secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let retry {
                    Button("重试", action: retry)
                        .font(.system(size: 14, weight: .semibold))
                        .buttonStyle(.borderless)
                }
            }
        }
    }
}

/// 未登录时的引导卡，点一下就打开登录面板。
struct WKLoginRequiredCard: View {
    let message: String
    var action: () -> Void

    var body: some View {
        GlassCardButton(action: action) {
            HStack(spacing: 14) {
                Image(systemName: "person.badge.key")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(.secondary)
                    .frame(width: 30)
                VStack(alignment: .leading, spacing: 3) {
                    Text("需要登录统一身份认证")
                        .font(.system(size: 16, weight: .semibold))
                    Text(message)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(WKInk.tertiary)
            }
        }
    }
}

/// 「标签 + 值」两列，详情页反复用。
struct WKInfoRow: View {
    let label: String
    let value: String
    var mono = false

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label)
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value)
                .font(.system(size: 14, weight: .medium, design: mono ? .monospaced : .default))
                .multilineTextAlignment(.trailing)
        }
    }
}

/// 小胶囊标签，成绩/状态这类用。
struct WKChip: View {
    let text: String
    var tint: Color? = nil

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(tint ?? Color.secondary)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background((tint ?? Color.primary).opacity(0.10), in: Capsule())
    }
}

/// 详情页顶部的摘要数字。
struct WKStatTile: View {
    let title: String
    let value: String
    var unit: String? = nil
    var tint: Color = .primary

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(value)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(tint)
                if let unit {
                    Text(unit)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 详情页统一的滚动容器：壁纸 + 内边距 + 隐藏指示器。
struct WKPageScroll<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                content
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .background(WKWallpaperBackground())
        .scrollIndicators(.hidden)
    }
}
