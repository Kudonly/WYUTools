import SwiftUI
import UIKit

/// 统一身份认证登录面板。账号密码 + 记住密码 +（必要时）滑块验证。
struct LoginView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var auth = AuthStore.shared

    @State private var account = ""
    @State private var password = ""
    @State private var remember = true
    @FocusState private var focus: Field?

    private enum Field { case account, password }

    var body: some View {
        NavigationStack {
            ZStack {
                WKWallpaperBackground()

                ScrollView {
                    VStack(alignment: .leading, spacing: WK.sectionSpacing) {
                        intro

                        GlassCard {
                            VStack(alignment: .leading, spacing: 14) {
                                field(title: "学号", text: $account, placeholder: "如 3125001460", secure: false)
                                    .focused($focus, equals: .account)
                                WKDivider()
                                field(title: "密码", text: $password, placeholder: "统一身份认证密码", secure: true)
                                    .focused($focus, equals: .password)

                                Toggle(isOn: $remember) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("记住密码")
                                            .font(.system(size: 14))
                                        Text("只存在本机钥匙串，会话过期时静默重登")
                                            .font(.system(size: 12))
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                .tint(.green)
                            }
                        }

                        if let error = auth.loginError {
                            Text(error)
                                .font(.system(size: 13))
                                .foregroundStyle(.red)
                                .padding(.horizontal, 4)
                        }

                        WKProminentButton(
                            title: auth.loggingIn ? "正在登录…" : "登录",
                            symbol: "person.badge.key",
                            enabled: !auth.loggingIn && !account.isEmpty && !password.isEmpty
                        ) {
                            focus = nil
                            Task {
                                await auth.login(account: account, password: password, remember: remember)
                                if auth.loggedIn { dismiss() }
                            }
                        }

                        Text("登录信息只用于访问学校系统，不会上传到任何第三方。")
                            .font(.system(size: 12))
                            .foregroundStyle(.tertiary)
                            .padding(.horizontal, 4)
                    }
                    .padding(.horizontal, WK.pagePadding)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
                }
                .scrollIndicators(.hidden)

                if let challenge = auth.captcha {
                    Color.black.opacity(0.38)
                        .ignoresSafeArea()
                        .transition(.opacity)

                    SliderCaptchaView(
                        challenge: challenge,
                        verifying: auth.captchaVerifying,
                        error: auth.captchaError,
                        onVerify: { payload in
                            Task {
                                await auth.submitCaptcha(payload)
                                if auth.loggedIn { dismiss() }
                            }
                        },
                        onRefresh: { Task { await auth.refreshCaptcha() } },
                        onDismiss: { auth.dismissCaptcha() }
                    )
                    .transition(.scale(scale: 0.94).combined(with: .opacity))
                }
            }
            .animation(.snappy(duration: 0.22), value: auth.captcha)
            .navigationTitle("登录教务账号")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") {
                        auth.dismissCaptcha()
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.large])
        .onAppear {
            account = auth.savedAccount
            if auth.hasSavedPassword { password = auth.savedPassword }
        }
    }

    private var intro: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("五邑大学统一身份认证")
                .font(.system(size: 22, weight: .bold))
            Text("登录后课表、成绩、考试、教评、体育馆都能直接用；密码用教务的 AES 加密后再提交。")
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 4)
        .padding(.bottom, 2)
    }

    private func field(
        title: String,
        text: Binding<String>,
        placeholder: String,
        secure: Bool
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            Group {
                if secure {
                    SecureField(placeholder, text: text)
                } else {
                    TextField(placeholder, text: text)
                        .keyboardType(.numberPad)
                        .textContentType(.username)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }
            }
            .font(.system(size: 16))
        }
    }
}

/// 滑块验证：拖动拼图到缺口，松手提交轨迹。
struct SliderCaptchaView: View {
    let challenge: SliderChallenge
    let verifying: Bool
    let error: String?
    let onVerify: (SliderPayload) -> Void
    let onRefresh: () -> Void
    let onDismiss: () -> Void

    private let canvasWidth: CGFloat = 280
    private let canvasHeight: CGFloat = 155
    private let handleSize: CGFloat = 40

    @State private var offset: CGFloat = 0
    @State private var dragStart: CGFloat = 0
    @State private var dragging = false
    @State private var tracks: [SliderTrack] = []
    @State private var lastA = 0
    @State private var lastTime = Date()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("请完成安全验证")
                    .font(.system(size: 16, weight: .semibold))
                Spacer()
                Button(action: onRefresh) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 15, weight: .semibold))
                }
                .disabled(verifying)
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 15, weight: .semibold))
                }
                .disabled(verifying)
            }

            puzzle

            slider

            Text(hint)
                .font(.system(size: 12))
                .foregroundStyle(error != nil ? Color.red : Color.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .foregroundStyle(.primary)
        .padding(18)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .padding(.horizontal, 24)
    }

    private var bigImage: UIImage? { UIImage(data: challenge.bigImage) }
    private var smallImage: UIImage? { UIImage(data: challenge.smallImage) }

    private var puzzle: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.primary.opacity(0.06))

            if let bigImage {
                Image(uiImage: bigImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: canvasWidth, height: canvasHeight)
                    .clipped()
            } else {
                Text("验证码图片加载失败，点右上角刷新")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
            }

            if let smallImage {
                Image(uiImage: smallImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: pieceWidth, height: canvasHeight)
                    .clipped()
                    .offset(x: offset)
            }
        }
        .frame(width: canvasWidth, height: canvasHeight)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var pieceWidth: CGFloat {
        guard let big = bigImage, let small = smallImage, big.size.width > 0 else {
            return canvasWidth / 3
        }
        let width = small.size.width * (canvasWidth / big.size.width)
        return min(max(width, 24), canvasWidth)
    }

    private var slider: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color.primary.opacity(0.08))
                .frame(width: canvasWidth, height: handleSize)

            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color.accentColor.opacity(0.18))
                .frame(width: offset + handleSize, height: handleSize)

            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(dragging ? Color.accentColor : Color(.secondarySystemBackground))
                .frame(width: handleSize, height: handleSize)
                .overlay {
                    if verifying {
                        ProgressView().controlSize(.mini)
                    } else {
                        Image(systemName: "chevron.right.2")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(dragging ? Color.white : Color.secondary)
                    }
                }
                .offset(x: offset)
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            guard !verifying else { return }
                            if !dragging {
                                dragging = true
                                dragStart = offset
                                tracks = [SliderTrack(a: 0, b: 0, c: 0)]
                                lastA = 0
                                lastTime = Date()
                            }
                            let maxOffset = canvasWidth - handleSize
                            let next = min(max(0, dragStart + value.translation.width), maxOffset)
                            offset = next
                            sample(Int(next.rounded()))
                        }
                        .onEnded { _ in
                            guard !verifying else { return }
                            dragging = false
                            let end = Int(offset.rounded())
                            let dt = Int(Date().timeIntervalSince(lastTime) * 1000)
                            tracks.append(SliderTrack(a: end, b: syntheticY(end), c: max(0, dt)))
                            onVerify(
                                SliderPayload(
                                    canvasLength: Int(canvasWidth),
                                    moveLength: end,
                                    tracks: tracks
                                )
                            )
                        }
                )
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: canvasWidth, height: handleSize)
    }

    private var hint: String {
        if verifying { return "校验中…" }
        if let error { return error }
        if dragging { return "松手完成验证" }
        return "拖动小拼图到缺口位置即可"
    }

    private func sample(_ a: Int) {
        let b = syntheticY(a)
        let dt = Int(Date().timeIntervalSince(lastTime) * 1000)
        let db = b - syntheticY(lastA)
        let distance = (pow(Double(a - lastA), 2) + pow(Double(db), 2)).squareRoot()
        guard dt >= 20, distance >= 2 else { return }
        tracks.append(SliderTrack(a: a, b: b, c: dt))
        lastA = a
        lastTime = Date()
    }

    private func syntheticY(_ a: Int) -> Int {
        Int((sin(Double(a) / 12.0) * 1.8).rounded())
    }
}

#Preview {
    LoginView()
        .environment(AppSettings.shared)
}
