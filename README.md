<div align="center">

# SimianV2 · 猿猴模块

**小猿口算 LSPosed 增强模块 · 基于 libxposed Modern API 101**

![Version](https://img.shields.io/badge/version-1.1.0-006A66)
![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![LSPosed API](https://img.shields.io/badge/LSPosed-API%20101-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE.txt)
[![QQ 群](https://img.shields.io/badge/QQ群-994173459-12B7F5)](https://qm.qq.com/q/994173459)

> **QQ 交流群：`994173459`**（问题反馈 / 获取更新 / 使用交流）

</div>

## 介绍

**SimianV2** 是一款面向 **小猿口算** 的 LSPosed / Xposed 增强模块。

模块使用 libxposed Modern API 101 注入宿主，通过 DexKit
动态定位混淆目标，并提供宿主内设置弹窗、答题增强、自动化操作、自定义数据和实时日志悬浮窗等功能。

> 所有模块配置均保存在小猿口算的私有目录中，不使用模块自身的 SharedPreferences。

## 功能

### 答题增强

- 快速答题。
- 自定义答案。
- 自定义题目数量。
- 自定义结束时间。
- 自动答题，可自定义每道题的等待时间。

> 自动答题需要同时开启“快速答题” 和 ”自定义题数(1)“功能。

### 自动化

- 自动点击“开心收下”。
- 自动点击“继续”。
- 自动点击“继续 PK”。
- 自动答题速度可在宿主设置弹窗中编辑。

### 用户与分数

- 解除昵称长度、字符和格式限制。
- 查询当前分数。
- 自定义增加分数并刷新结果。

### 设置与调试

- 在小猿口算内注入模块设置入口。
- 设置项实时保存到宿主私有 JSON 文件。
- 实时日志悬浮窗。
- 悬浮窗支持拖动和缩小为圆形。
- 模块本体通过 libxposed service 检测激活状态。
- Release 构建自动移除 Logcat 和 LSPosed 调试日志。

### 混淆适配

- 每个 Hook 独立维护自己的 DexKit 查询规则。
- 多个 Hook 共用一次 DexKit 会话，避免重复解析 APK。
- 定位结果保存到宿主私有目录。
- 宿主版本未变化时直接从缓存恢复目标，不重新扫描。
- 宿主版本变化、缓存损坏或目标失效时自动重新定位。
- 单个功能定位失败不会阻止其他 Hook 安装。

## 当前支持

| 项目      | 支持范围                      |
|---------|---------------------------|
| Android | Android 12 及以上（最低 API 31） |
| 编译目标    | API 37                    |
| LSPosed | Modern API 101            |
| Root    | 需要可用的 LSPosed 环境          |

> 项目使用 DexKit 特征定位和版本缓存提升混淆版本兼容性，但宿主更新仍可能导致部分功能失效。遇到问题请携带宿主版本和相关日志前往
> QQ 群反馈。

## 安装与使用

1. 从项目的 [Releases](../../releases) 下载并安装 APK
2. 在 LSPosed 中启用 **SimianV2**
3. 将作用域勾选为小猿口算
4. 强制停止并重新启动小猿口算
5. 首次进入主页时等待 DexKit 初始化完成
6. 打开小猿口算中的模块设置入口，按需启用功能

## 配置与缓存

模块数据位于小猿口算私有目录：

```text
files/simian/settings.json
files/simian/dexkit_targets.json
```

- `settings.json`：保存功能开关和自定义数值。
- `dexkit_targets.json`：保存类名、方法名、参数类型以及对应的宿主版本。

卸载小猿口算或清除其应用数据会同时删除这些配置。

## 常见问题

### 初始化进度弹窗报错

宿主更新后，模块会自动废弃旧 DexKit 缓存并重新定位。如果某个功能定位失败，请记录功能名称、宿主版本和异常日志。

### 自动答题没有生效

同时开启“自动答题”和“快速答题”，确认已正确设置答题速度，然后重新进入 PK 页面。

### 设置入口没有出现

先进入一次小猿口算个人中心或包含用户设置列表的页面。如果仍未出现，请检查 `SettingsEntryHook` 初始化日志。

### 功能修改后没有立即生效

大部分设置会实时读取；涉及页面初始化或前端脚本状态的功能，建议退出当前页面重新进入，必要时强制停止宿主后重试。

## 技术说明

模块在 `Application.attach` 后获取宿主 Context 和 ClassLoader，并 Hook `HomeActivity`
展示初始化进度。各功能通过统一的 `BaseHook` 生命周期完成：

```text
initializeDexKit() → install()
```

DexKit 定位结果按功能独立缓存。缓存使用宿主的 `versionCode`、`versionName` 和 `lastUpdateTime`
校验；版本一致时直接反射恢复，只有缓存缺失或失效时才加载 DexKit 扫描 APK。

Release 构建启用 R8，移除 Android Log 和 libxposed 日志调用，同时保留用户主动开启的日志悬浮窗功能。

## 更新日志

### V2 · v1.1.1 · 2026-09-06

- 修复自动答题功能

### V2 · v1.1 · 2026-09-02

- 新增自动化设置页面。
- 新增自动答题和答题速度设置。
- 新增自动点击“开心收下”。
- 新增自动点击“继续”。
- 新增自动点击“继续 PK”。
- 自动答题需要搭配快速答题使用。

### V2 · v1.0 · 2026-08-28

- 重写模块主界面与宿主设置弹窗。
- 支持快速答题和自定义答案。
- 支持自定义题数和自定义结束时间。
- 支持解除名字限制。
- 支持日志悬浮窗和圆形收起模式。
- 支持自定义分数查询与提交。
- 配置迁移到宿主私有 JSON 文件。
- 使用 DexKit 动态定位目标并按宿主版本缓存。
- 使用 libxposed API 101 检测模块激活状态。

### V1 旧版日志（已停止维护）

- ~~v1.0：答案全部更换为 `.`；快速回答。~~
- ~~v1.1：增加两种自定义答案模式。~~
- ~~v1.2：重写 UI、支持自定义题目、增加远程更新。~~
- ~~v1.3：支持口算练习自定义答案。~~

## 鸣谢

- [LuckyPray/DexKit](https://github.com/LuckyPray/DexKit) —— 运行时 DEX 查询支持。
- [libxposed/api](https://github.com/libxposed/api) —— Modern Xposed API。
- [LSPosed/LSPosed](https://github.com/LSPosed/LSPosed) —— Xposed 运行环境。

## 许可

本项目采用 [MIT License](LICENSE.txt)。

---

本项目与猿辅导、小猿口算官方无关，仅供学习交流。请遵守相关法律法规和软件服务条款，使用者需自行承担使用风险。
