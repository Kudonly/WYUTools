import SwiftUI

struct SettingsView: View {
    @AppStorage("glass.blur") private var blur: Double = 0.6
    @AppStorage("schedule.weekend") private var showWeekend = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                glassCard
                scheduleCard
                diagnosticsCard
                aboutCard
                Color.clear.frame(height: 40)
            }
            .padding(.horizontal, WK.pagePadding)
            .padding(.top, 8)
        }
        .background(PageBackground())
        .scrollIndicators(.hidden)
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var glassCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("柔光玻璃")
                    .font(.system(size: 16, weight: .semibold))
                HStack {
                    Text("模糊度")
                        .font(.system(size: 14))
                    Spacer()
                    Text("\(Int(blur * 100))%")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
                Slider(value: $blur, in: 0...1)
                    .tint(WKPalette.accent)
                Text("iOS 上用系统 Material 实现，模糊由系统实时采样，不需要自写 shader。")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var scheduleCard: some View {
        GlassCard {
            Toggle("课表显示周六日", isOn: $showWeekend)
                .font(.system(size: 15))
                .tint(WKPalette.accent)
        }
    }

    private var diagnosticsCard: some View {
        NavigationLink {
            NetworkCheckView()
        } label: {
            GlassCard {
                HStack(spacing: 14) {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(WKPalette.accent)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("接口连通性检测")
                            .font(.system(size: 15, weight: .medium))
                        Text("P0：验证五个后端域在 iOS 上能否访问")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .buttonStyle(CardPressStyle())
    }

    private var aboutCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 6) {
                Text("关于")
                    .font(.system(size: 15, weight: .medium))
                Text("WUKit for iOS · 0.1.0 骨架")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                Text("原生 SwiftUI 复刻，网络层与解析待 P0/P1 接入。")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

#Preview {
    NavigationStack {
        SettingsView()
    }
}
