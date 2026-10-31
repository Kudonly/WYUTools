# WYUtools

## 简介

重磅推出“WYUtools”！

旨在解决日常校园生活高频功能需要各种💩小程序，APP，网页来回使用的不便，体积小巧不超过50Mb。

五邑大学校园服务的一站式 Android 工具，把日常高频的校园功能收进一个轻量应用。
使用 Kotlin + Jetpack Compose + Miuix 构建，
界面参考 HyperOS 4：透明渐进顶栏、大标题折叠、悬浮底栏、卡片一镜到底转场与磨砂玻璃卡片。

## 功能

- **洗澡**：热水余额、设备绑定与开阀，支持用水时长统计。
- **电费**：余额查询、用电趋势、充值记录。
- **体育馆**：场地与时段查询、在线预约，按学期统计预约时长与抵消校园跑公里数。
- **我**：统一认证登录、个人信息、教务系统入口（支持演示模式）。
- **设置**：预设壁纸与相册自定义壁纸、背景明暗调节、演示模式。

## 界面展示

> 截图中的楼栋、房间与订单号等信息已作遮挡处理。

### 工具主页

<p align="center"><img src="docs/images/home.jpg" width="300" alt="工具主页"></p>

首页以卡片聚合校园服务，当前提供洗澡、电费与体育馆预约；卡片支持一镜到底转场，配合透明渐进顶栏、悬浮底栏与自定义壁纸。

### 洗澡

<p align="center"><img src="docs/images/lxt.jpg" width="300" alt="洗澡"></p>

查看热水、冷水余额，更换热水设备，一键开启热水阀；开阀后开始计费。

### 电费

<table>
<tr>
<td align="center"><img src="docs/images/elec-query.jpg" width="235" alt="电费查询"><br>查询余额</td>
<td align="center"><img src="docs/images/elec-records.jpg" width="235" alt="电费记录与用电趋势"><br>充值记录与用电趋势</td>
<td align="center"><img src="docs/images/elec-recharge.jpg" width="235" alt="电费充值与按月统计"><br>充值与按月统计</td>
</tr>
</table>

- **查询**：输入楼栋与房间号后可记住宿舍，展示剩余电量、折合电费、近期日均与预计可用天数。
- **记录**：按笔查看充值记录，支持查看全部记录，并提供用电趋势与本月用电、电费统计。
- **充值**：输入金额通过学校电费系统生成支付二维码，走官方渠道，支持微信/支付宝扫码支付，并按月汇总充值金额。

### 体育馆预约

<table>
<tr>
<td align="center"><img src="docs/images/gym-booking.jpg" width="280" alt="体育馆场地预约"><br>场地预约</td>
<td align="center"><img src="docs/images/gym-my.jpg" width="440" alt="体育馆我的预约"><br>我的预约 · 学期时长与校园跑抵消计算</td>
</tr>
</table>

- **场地预约**：按日期、体育项目与场馆筛选，查看可约时段与单次最长预约时长，直接选择时段预约。
- **我的预约**：按学期统计已使用预约时长与场次；每 80 分钟折算 4 公里，可直接查看本学期抵消的校园跑公里数（图中为 640 分钟，抵消 32 公里）。

### 我

<p align="center"><img src="docs/images/me.jpg" width="300" alt="我"></p>

统一认证登录后展示个人信息，可直接打开教务系统；退出登录会清除本机保存的会话。开启演示模式后，个人信息会替换为预设资料。

### 设置

<p align="center"><img src="docs/images/settings.jpg" width="300" alt="设置"></p>

支持相册自定义壁纸与预设壁纸，可调节背景亮度；演示模式开关位于此处。

## 下载

前往本仓库的 **Releases** 页面下载最新的apk文件

- 系统要求：Android 8.0（API 26）及以上
- 安装包约 1.8 MB

## 构建

需要 JDK 17 与 Android SDK（compileSdk 37）。

```bash
./gradlew :app:assembleRelease
```

产物位于 `app/build/outputs/apk/release/app-release.apk`。
`app/libs/` 下为构建所需的外部库（二维码库、SharedViewKit），请勿删除。

## 说明

- 本项目为非官方工具，仅供学习交流，与学校及任何官方平台无关，请遵守学校相关规定。
- 登录凭据仅保存在本机，并通过 Android Keystore 加密；请勿在不可信设备上登录。
- 开阀、充值等操作会产生实际费用，请谨慎确认后再执行。
- 部分功能依赖校园网络环境，具体可用性以学校平台为准。

## 开源协议

本项目代码基于 [MIT License](LICENSE) 开源。第三方依赖与 `app/libs/` 下的预编译库遵循其各自的许可协议，不在 MIT 许可范围内。

