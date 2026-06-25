# iDRAC 控制器 - Android App

通过 Android 手机远程控制 Dell 服务器电源（开机 / 关机 / 重启 / 查询状态），支持 SSH (racadm) 连接。

如果这个项目对你有帮助，欢迎给个 Star ⭐ — 你的支持是我持续改进的动力！

[![Stars](https://img.shields.io/github/stars/tblfokb/idrac-controller?style=social)](https://github.com/tblfokb/idrac-controller/stargazers)

## 应用截图

![主界面截图](screenshots/screenshot_main.png)

*暗色科技风主界面 - 卡片式布局，状态面板与控制面板分区显示*


## 功能

- 远程开机 / 关机 / 重启 Dell 服务器
- 查询电源状态、传感器信息、硬件信息
- 支持 **SSH (racadm)** 连接方式
- 自定义 SSH 端口（适配端口映射场景）
- 保存 iDRAC 连接配置
- **暗色科技风 UI** — 深蓝黑底色 + 青色强调，卡片式布局，色彩编码按钮（绿=开机 / 红=关机 / 橙=重启）
- **电源操作确认弹窗** — 防止误触导致意外关机/重启
- **一键查询** — 同时获取电源状态、传感器数据、硬件信息（CPU/内存/系统信息）
- **科技感图标** — 深蓝黑+青色配色，服务器机柜+闪电图案
- **SSH 终端** — 内置 SSH 终端，可直接输入 racadm 命令（v1.9.2 新增）

## 支持的 iDRAC 版本

| iDRAC 版本 | SSH 模式 |
|------------|---------|
| iDRAC 7 | 支持 |
| iDRAC 8 | 支持 |
| iDRAC 9 | 支持 |

## 安装

从 [Releases](https://github.com/tblfokb/idrac-controller/releases) 页面下载最新 APK，在 Android 设备上安装即可。

最低要求：Android 5.0 (API 21)

## 使用说明

### 1. 配置连接
1. 打开 App，点击右上角设置图标
2. 输入 iDRAC IP 地址
3. 输入用户名（默认 `root`）和密码
4. （可选）自定义 SSH 端口号 — 做了端口映射时可在此修改
5. 点击保存

### 2. 控制电源
- **查询服务器状态** — 一键获取电源状态、传感器数据、硬件信息
- **开机** — 发送开机指令（有确认弹窗）
- **关机** — 强制关机（有确认弹窗）
- **重启** — 强制重启（有确认弹窗）

## 端口映射

如果需要从外网访问 iDRAC，在路由器映射以下端口：

| 端口 | 协议 | 用途 |
|------|------|------|
| 22 | TCP | SSH (racadm) |

映射后，在 App 设置中将端口号改为映射后的外部端口即可。

## 构建

### 前置条件
- JDK 17+
- Android SDK (platforms;android-35, build-tools;35.0.0)
- Python 3.6+ (用于构建脚本)

### 快速构建
```bash
# Windows
python build_apk.py

# 或使用 bat
build_apk.bat
```

构建产物在 `out/apk/app-debug.apk`。

### 依赖库
- JSch 0.1.55 (SSH 连接)
- Gson 2.10.1 (JSON 解析，预留)
- OkHttp 4.12.0 (HTTP 客户端，预留)
- Kotlin Stdlib 2.0.21

## 安全建议

- 不要将 iDRAC 直接暴露在公网
- 使用强密码
- 推荐通过 VPN 访问内网 iDRAC
- 定期更新 iDRAC 固件

## 许可

MIT License

---

## 更新日志

### v1.11.0 (2026-06-25)
- 硬件信息完整中文化（自动翻译英文术语：系统型号、服务标签、BIOS版本、电源状态等）
- 移除调试按钮（"原始输出"），界面更简洁
- 代码清理，移除调试日志和相关方法
- 版本号更新到 versionCode=19, versionName=1.11.0

### v1.10.0 (2026-06-25)
- 修复硬件信息显示不正确的问题
- 优化硬件信息解析逻辑

### v1.9.2 (2026-06-25)
- 新增 SSH 终端功能（可直接在 App 内输入 racadm 命令）
- 支持连接/断开连接、清屏、帮助命令
- 版本号更新到 versionCode=16, versionName=1.9.2

### v1.9.1 (2026-06-25)
- 添加科技感 APP 图标（深蓝黑 + 青色配色，服务器机柜 + 闪电图案）
- 支持多密度图标（mdpi ~ xxxhdpi）+ 圆角图标
- 添加 Play Store 图标 (512x512)
- 版本号更新到 versionCode=15, versionName=1.9.1

### v1.9.0 (2026-06-25)
- 移除优雅关机和优雅重启功能（iDRAC SSH 兼容性原因）
- 为电源控制按钮（开机/关机/重启）添加确认弹窗，防止误触
- 界面全中文化

### v1.8.0 (2026-06-24)
- 优化硬件信息解析逻辑（支持 `Key = Value` 格式）
- 添加 CPU 信息获取和显示
- 添加内存信息获取和显示
- 硬件信息展示更全面

### v1.7.1 (2026-06-24)
- 修复硬件信息显示不正确的问题
- 改为显示完整清理后的 `racadm getsysinfo` 输出
- 硬件信息 TextView 支持滚动查看

### v1.7.0 (2026-06-24)
- 界面全面美化（渐变背景、按钮渐变效果、emoji 图标）
- 速度优化（一次 SSH 会话同时获取传感器+硬件信息+CPU/内存信息）
- 硬件信息增强展示

### v1.6.1 (2026-06-24)
- "查询服务器状态"按钮整合电源状态+传感器+硬件信息查询
- 移除"刷新传感器"/"详情"冗余按钮
- 界面更简洁

### v1.6.0 (2026-06-24)
- 速度优化（一次 SSH 会话获取所有信息）
- 界面全面中文化
- 首页自动加载传感器和硬件信息

### v1.5.4 (2026-06-24)
- 修复硬件信息解析（`racadm getsysinfo` 点号分隔格式）
- 首页 onResume 自动加载传感器和硬件信息

### v1.5.3 (2026-06-24)
- 修复 SSH 传感器/硬件数据获取（ChannelExec优先+ChannelShell回退）
- 首页增加硬件信息卡片直接展示

### v1.5.2 (2026-06-24)
- 修复状态栏遮挡（主题改用 DeviceDefault.NoActionBar + 状态栏颜色 + fitsSystemWindows）
- minSdk 提升到 21

### v1.5.1 (2026-06-24)
- SSH 改用 ChannelShell（iDRAC 兼容性修复）
- 移除设置页 HTTPS 端口输入框

### v1.5.0 (2026-06-24)
- 取消 Redfish，纯 SSH 模式
- 移除所有 OkHttp/Gson Redfish HTTP 调用
- 所有接口直连 SSH racadm

### v1.4.0 (2026-06-24)
- 移除 Mode 选择框，配置为 Redfish+SSH 双模式自动 fallback 运行

### v1.3.1 (2026-06-24)
- 修复 iDRAC 8 兼容性 — discoverMember() 自动发现 Chassis/Systems 路径

### v1.3 (2026-06-24)
- 液态玻璃效果 + 传感器监控/硬件清单/多服务器管理/优雅关机重启

### v1.2 (2026-06-24)
- 暗色科技风 UI 全面美化
- 卡片式分区布局，状态与控制面板分离
- 圆角按钮和输入框，颜色编码区分功能

### v1.1 (2026-06-24)
- 修复 Android 16 闪退问题（NoClassDefFoundError: Gson）
- 新增自定义 SSH 端口号功能

### v1.0 (2026-06-24)
- 首个版本，支持 SSH 远程控制

---

## ⭐ Star History

如果你觉得这个项目有用，请给我一个 Star！你的支持是我持续更新的动力 🙏

[![Star History Chart](https://api.star-history.com/svg?repos=tblfokb/idrac-controller&type=Date)](https://star-history.com/#tblfokb/idrac-controller&Date)

## 致谢

如果这个项目对你有帮助，欢迎：
- ⭐ 给项目点个 Star
- 🍴 Fork 并改进功能
- 🐛 提交 Issue 报告问题
- 📢 分享给更多需要的人
