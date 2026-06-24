# iDRAC 控制器 项目记忆

## 项目信息
- 项目路径: C:\Users\huc_o\AndroidStudioProjects\iDracController
- GitHub: https://github.com/tblfokb/idrac-controller
- APK 命名: iDRAC控制器_v{版本号}.apk (桌面)

## 构建
- 构建脚本: build_apk.py
- 依赖库 (libs/): gson-2.10.1.jar, okhttp-4.12.0.jar, okio-3.6.0.jar, jsch-0.1.55.jar, kotlin-stdlib-2.0.21.jar
- d8 命令必须包含 libs/*.jar，否则 APK 闪退 (NoClassDefFoundError)

## 当前工作区
- 工作目录: C:\Users\huc_o\WorkBuddy\Claw (使用 GitHub MCP 拉取代码)
- 构建脚本已适配此路径

## 版本历史
- v1.1: 修复 Android 16 闪退，新增自定义端口号功能
- v1.2: 暗色科技风 UI 美化（深蓝黑底色+青色强调色，卡片式布局，圆角按钮/输入框）
- v1.3: 液态玻璃效果 + 传感器监控/硬件清单/多服务器管理/优雅关机重启
- v1.3.1: 修复 iDRAC 8 兼容性 — discoverMember() 自动发现 Chassis/Systems 路径
- v1.4.0: 移除Mode选择框，配置为 Redfish+SSH 双模式自动fallback运行
- v1.5.0: 取消Redfish，纯SSH模式 — 移除所有OkHttp/Gson Redfish HTTP调用，所有接口直连SSH racadm
- v1.5.1: SSH改用ChannelShell（iDRAC兼容性修复）；移除设置页HTTPS端口输入框
- v1.5.2: 修复状态栏遮挡 — 主题改用 DeviceDefault.NoActionBar + 状态栏颜色 + fitsSystemWindows，minSdk 提升到 21
- v1.5.3: 修复SSH传感器/硬件数据获取（ChannelExec优先+ChannelShell回退）；首页增加硬件信息卡片直接展示
- v1.5.4: 修复硬件信息解析（`racadm getsysinfo` 点号分隔格式）；首页 onResume 自动加载传感器和硬件信息
- v1.6.0: 速度优化（一次SSH会话同时获取传感器+硬件信息）；界面全面中文化
- v1.6.1: "查询服务器状态"按钮整合电源状态+传感器+硬件信息查询；移除"刷新传感器"/"详情"冗余按钮
