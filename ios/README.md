# WUKit for iOS

原生 SwiftUI 复刻，与 Android 版共用同一套校园接口。当前 0.2：**原生壳 + 主题系统已完成**，底栏是 iOS 26 的系统液态玻璃 TabView，常用 / 更多 / 我三页的位置、间距、卡片顺序按 Android 对齐；9 个工具里「今日」「课表」已有本地数据，其余是标明「接口待接入」的占位页。

## 结构

```
ios/
├── project.yml              # XcodeGen 工程定义（.xcodeproj 由它生成，不入库）
└── WUKit/
    ├── App/                 # @main + 原生 TabView 根视图
    ├── Core/
    │   ├── Models/          # WKTool、TodoItem、Course
    │   ├── Network/         # HTTPClient、Endpoints（五个后端域）
    │   ├── Notifications/   # CourseReminder（上课提醒本地通知）
    │   ├── Settings/        # AppSettings（壁纸/深浅色/材质/强调色/布局）
    │   └── Storage/         # TodoStore、ScheduleStore、日期工具
    ├── Design/              # 液态玻璃材质、组件、图标块、调色板
    ├── Features/            # Home / More / Me / Today / Schedule / Settings
    └── Resources/           # Assets、Info.plist（生成）
```

## 设计

- **底栏**：系统 `TabView` + `Tab`，iOS 26 起自动是原生液态玻璃；`tabBarMinimizeBehavior(.onScrollDown)` 让滚动时收起。详情页用 `.toolbar(.hidden, for: .tabBar)` 整页铺开，和 Android 一致。
- **卡片**：`glassEffect` 走系统 Liquid Glass（带高光、折射与触感），老系统退回 `Material`；圆角 28、内边距 18、页边距 16 与 Android 同一套数值。
- **主题**：壁纸（晨雾 / 青岚 / 晚霞 / 极光 / 相册照片）、深色浅色跟随系统、卡片材质（磨砂 / 液态 / 实色）、强调色、玻璃着色强度，全部即时生效并持久化。
- **布局管理**：常用页卡片与更多页图标的归属、顺序都可改，默认值与 Android 相同（常用：课表、今日、洗澡、电费、体育馆）。

## 本地构建

需要最新正式版 Xcode（iOS 26 SDK 才有液态玻璃）+ [XcodeGen](https://github.com/yonaskolb/XcodeGen)。低版本 SDK 也能编过，只是液态玻璃那段会被 `#if compiler(>=6.2)` 跳过。

```bash
brew install xcodegen
cd ios
xcodegen generate
open WUKit.xcodeproj
```

命令行出无签名 IPA：

```bash
xcodebuild -project WUKit.xcodeproj -scheme WUKit -configuration Release \
  -sdk iphoneos -destination "generic/platform=iOS" -derivedDataPath build \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO build

mkdir -p out/Payload
cp -R build/Build/Products/Release-iphoneos/WUKit.app out/Payload/
(cd out && zip -qry WUKit-unsigned.ipa Payload)
```

## CI

`.github/workflows/ios.yml`：push 到 `ios/**` 或手动触发。构建前会自动挑选 runner 上**最新的正式版 Xcode**，打印 Xcode 与 iPhoneOS SDK 版本；产物是带版本号与时间戳的 IPA，放在 Actions 的 `WUKit-unsigned-ipa` artifact 里。

**产物没有签名，不能直接装。** 装法：TrollStore、AltStore / Sideloadly 自签，或用自己的开发者证书重签。

## 接口状态

App 内「我 → 设置 → 接口连通性检测」，一键打五个后端域，看状态码。

| 域 | 用途 | 已知注意点 |
|---|---|---|
| authserver.wyu.edu.cn | 统一认证 | AES-CBC 密码 + 滑块验证码，待移植 |
| jxgl.wyu.edu.cn | 教务 | 需 SSO 换票 cookie，HTML/JSON 解析待移植 |
| ehall.wyu.edu.cn | 体育馆 | SSO cookie + 卡片态 |
| 202.192.240.231 | 电费 | **明文 HTTP**，Info.plist 已开 ATS 例外；UA 要伪装 Android |
| v3-prod-beta-app.lxt6.cn | 热水 | 自建账号 + MD5 签名头，UA 要伪装 Android |

电费是校内地址，通常只有校园网可达。5G 打不开属正常，不是移植问题。
