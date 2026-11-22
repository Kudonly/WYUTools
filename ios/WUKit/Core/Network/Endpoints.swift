import Foundation

/// 五个后端域。P0 阶段先用它按顺序验证连通性。
enum WKEndpoint {
    /// 统一身份认证（金智 authserver）：登录、滑块、退出。
    static let auth = "https://authserver.wyu.edu.cn/authserver"
    /// 教务系统：课表、成绩、考试、教评、体测。
    static let jxgl = "https://jxgl.wyu.edu.cn/new"
    /// 一站式服务大厅：体育馆预约。
    static let ehall = "https://ehall.wyu.edu.cn"
    /// 电费：明文 HTTP，Info.plist 已开 ATS 例外。
    static let elec = "http://202.192.240.231"
    /// 热水（第三方 SaaS，独立账号与签名头）。
    static let lxt = "https://v3-prod-beta-app.lxt6.cn"
    /// 检查更新。
    static let github = "https://api.github.com"
}

enum WKUA {
    /// 电费与热水都认 Android UA，iOS 必须显式伪装。
    static let legacyAndroid = "okhttp/3.12.1"
    static let wyuAndroid = "WYUTools/1.0 (Android)"
}
