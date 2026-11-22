# WUKit for iOS

原生 SwiftUI 复刻，与 Android 版共用同一套校园接口。当前是**工程骨架**：导航、玻璃卡、待办、课表都能跑，9 个工具里只有「今日」「课表」有真实本地数据，其余是占位页。

## 结构

```
ios/
├── project.yml              # XcodeGen 工程定义（.xcodeproj 由它生成，不入库）
└── WUKit/
    ├── App/                 # @main + 三页根视图
    ├── Core/
    │   ├── Models/          # WKTool、TodoItem、Course
    │   ├── Network/         # HTTPClient、Endpoints（五个后端域）
    │   └── Storage/         # TodoStore、ScheduleStore、日期工具
    ├── Design/              # 玻璃卡、底栏、图标块、调色板
    ├── Features/            # Home / More / Me / Today / Schedule / Settings
    └── Resources/           # Assets、Info.plist（生成）
```

## 本地构建

需要 Xcode 16 + [XcodeGen](https://github.com/yonaskolb/XcodeGen)。

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

`.github/workflows/ios.yml`：push 到 `ios/**` 或手动触发，macOS runner 上生成工程、编译、打包 IPA，产物在 Actions 的 `WUKit-unsigned-ipa` artifact 里。

**产物没有签名，不能直接装。** 装法：TrollStore、AltStore / Sideloadly 自签，或用自己的开发者证书重签。

## P0 验证

App 内「我 → 设置 → 接口连通性检测」，一键打五个后端域，看状态码。

| 域 | 用途 | 已知注意点 |
|---|---|---|
| authserver.wyu.edu.cn | 统一认证 | AES-CBC 密码 + 滑块验证码，待移植 |
| jxgl.wyu.edu.cn | 教务 | 需 SSO 换票 cookie，HTML/JSON 解析待移植 |
| ehall.wyu.edu.cn | 体育馆 | SSO cookie + 卡片态 |
| 202.192.240.231 | 电费 | **明文 HTTP**，Info.plist 已开 ATS 例外；UA 要伪装 Android |
| v3-prod-beta-app.lxt6.cn | 热水 | 自建账号 + MD5 签名头，UA 要伪装 Android |

电费是校内地址，通常只有校园网可达。5G 打不开属正常，不是移植问题。
