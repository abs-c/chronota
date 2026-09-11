# Chronota

以独立的 **Plan（原本打算）** 与 **Record（实际发生）** 为核心的
local-first Android 时间管理应用。无账号、无云同步。

当前为 **0.1.1 预览版**，包标识统一为 `app.chronota`。已实现日常使用所需的完整链路：
计划与记录管理、双栏时间轴、正计时/番茄钟、提醒、循环计划、两级分类与自定义属性、
目标、回顾统计，以及本地文件与 WebDAV 备份。详细进度见 [里程碑](docs/ROADMAP.md)。

> 0.1.0 采用**全新的应用标识**。旧包 `app.chronotation`（0.10.0）的数据不会自动继承，
> 需在旧版本用「备份到文件 / WebDAV」导出后，在本版的「我的 → 备份」中导入。

## 构建与启动

需要 JDK 17 或 21、Android SDK Platform 36、Build Tools 35.0.0。
支持 Android 8.0（API 26）及以上；compileSdk / targetSdk 为 36。
Android Studio 需支持 AGP 8.13.2。

1. 使用 Android Studio 打开项目根目录，安装所需 SDK，并完成 Gradle Sync。
2. 在 `local.properties` 中设置本机 SDK 路径（Studio 通常自动生成）：

   ```properties
   sdk.dir=C\:/Users/your-name/AppData/Local/Android/Sdk
   ```

3. 运行以下命令构建并验证：

   ```powershell
   .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ```

4. 连接 Android 设备或启动模拟器，在 Studio 中运行 `app`，或执行：

   ```powershell
   .\gradlew.bat :app:installDebug
   adb shell am start -n app.chronota/.MainActivity
   ```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
首次构建需联网从 Google Maven、Maven Central、Gradle 下载依赖。
官方 Gradle Wrapper 已随项目提供，发行包使用 SHA-256 校验。

本机可复用忽略目录中已经下载的工具与缓存：

```powershell
$env:GRADLE_USER_HOME = "$PWD/.gradle-user-home"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

`.tools`（SDK、AVD）与 `.gradle-user-home` 都被忽略、不属于源代码。当前工作区通过目录
联接复用上一工作区已下载的工具链；换机器时按上面的标准安装流程准备即可。

## 发布 release

```powershell
.\scripts\release.ps1
```

脚本读取 `app/build.gradle.kts` 的 `versionName`，执行 `:app:assembleRelease`，并把生成的
`app-release.apk` 复制为桌面 `Chronota-<版本>.apk`。**此后每个 release 都复制一份到桌面**，
按版本命名以便区分。release 用本机 debug 签名（便于直接安装评审）并开启 R8 压缩。

## 当前操作

- **今日**：周视图显示七天，点击日期切换，横滑切换一周；点击月份选择日期，右侧“今日”返回当天。“我的 → 今日页周视图”开关可改为仅当日。时间刻度和计划占左半边，记录占右半边；全天计划单独显示。
- **时间轴**：固定按 15 分钟吸附，全天刻度压缩为约 1.3 屏。长按空白并拖动，松手打开预填起止时间的编辑窗口。过去只能补记录，未来只能建计划。长按过去计划直接补记录；拖动未来计划后进入时间已更新的编辑窗口，保存后生效。
- **底栏与悬浮球**：今日、计划、回顾、我的四个入口。计划页短按新建计划，回顾页短按新建记录；今日和我的使用设置指定的动作，图标随动作改变；长按带蓄力动画，展开四个圆形选项和原地取消，背景模糊变暗。拖到选项松手执行，拖出选项松手取消，也支持展开后点选。
- **计划**：日程使用“全部／各分类”筛选，按日期分组，可切换详细属性和正序／倒序；日历提供日／周／月视图，可叠加显示记录。日历只显示当前时刻之后的计划和之前的记录，图例随叠加开关变化。月历选中日期后，下方列出事项，点击“查看当日”进入时间轴。表单包含共享分类、全天事件、开始/结束、循环、提醒、分类属性；支持跨日。分类属性全部选填。
- **循环计划**：计划可设不循环／每天／每周／每月／每年，并设置间隔与重复结束日期；规则只存一份，实例由集中领域逻辑按可见范围展开，不写入数据库。月内 31 号在短月自动落到月末。循环计划关闭拖动移动，提醒跟随下一个未开始的实例。
- **目标**：在“计划 → 目标”中按“标题正则 + 属性筛选”圈定一组记录，设置指标（时长／次数／数值／评分等）、方向（至少达到／保持在上限内）、目标值、时间范围与重复方式，实时显示进度；达到目标后可选择“重启”，已完成周期作为历史保留。
- **记录**：从回顾或加号补录，也可以从计划复制标题、分类和已填写属性。记录独立保存，不建立计划关联，修改或删除计划不影响记录。
- **计时**：统一从“计时”入口进入后选择正计时或番茄钟；“计时设置”保存默认模式、专注/休息分钟数和轮数。两种计时均支持暂停、恢复和结束，状态与已填属性持久保存。分类中的必填属性在开始前校验。暂停及番茄钟休息不计入记录；关闭面板不会停止计时。
- **分类**：“我的 → 分类”管理两级分类。长按拖动一级分类排序；子分类可在网格中重排或拖入其他分组，靠近列表边缘自动滚动。分类名称左侧的图标可打开图标、颜色两个标签页：192 个 Lucide 圆润线性图标，98 个 OKLCH 配色，每行同色相、每列改变明度与饱和度。分类选择沿用分组网格，不提供编辑功能。
- **标题与日期**：任务标题默认提供，可删除或重新添加，最多一个。留空时显示分类名；“我的 → 事项名称显示”可设置优先显示标题或分类。表单按分类、标题与属性、起止时间排列；日期和时间在同一面板确认。
- **一天的起点**：“我的 → 新一天开始时间”设置日界线，默认 00:00。若设为 04:00，凌晨 03:00 归前一天；今日、日程、日周月历和统计共用此规则，跨边界时长分摊，存储的实际时间不变。
- **任务属性**：创建子分类时即可添加文本、数字、是否、单选、多选或评分属性，并设置必填；单选、多选的每个选项均可设颜色及默认值，选择面板按两列排列；选填项在“关闭”旁提供“清除”。评分以 0–100 整数分滑动，按最近的半星折算显示；表单右侧只显示星星。已有 0–5 星存储值保持兼容。文本属性可代替原有备注栏。分类及其新属性一起保存。文本可选单行短文本或初始三行、随内容增高的长文本。删除分类前显示影响数量，确认后删除其子分类、计划、记录、属性与提醒；其中正在进行的计时也会停止并丢弃。单独删除使用中的属性仍受引用保护。
- **回顾**：日程按分类筛选记录并按日期浏览；日历提供日／周／月视图，可叠加显示计划；统计支持日、周、月或自定义范围（最多 366 天），使用占比圆环、每日柱状趋势和活动日历，可按一级分类筛选并下钻二级分类。重叠记录分别累计。
- **历史策略**：默认锁定已结束计划，可在设置中开启编辑；进行中的计划可调整剩余结束时间。
- **提醒**：可设多个开始前提醒。Android 13+ 首次启用提醒或计时会请求通知权限；设置页提供通知与精确闹钟入口。后台限制和系统权限会影响提醒时效。
- **备份**：设置页“备份”分组提供三条路径。备份到文件／从文件恢复走系统文件选择器，不需要存储权限，导出文件名为 `chronota-<时间>.json`，恢复会先确认并整体替换。WebDAV 备份可填地址、账号、密码与目录，支持“测试连接”“立即备份”“从云端恢复”，点“立即备份”会先把所填信息保存下来再上传，不必先按“保存”；云端只维护一个滚动文件 `chronota-latest.json`。自动备份按设定钟点每天一次，手机重启或改时间后自动重排。恢复在单个事务内完成，任一步失败整体回滚。运行中的计时器不进入备份。
- **外观**：浅色、深色、跟随系统；语言在应用内选择跟随系统、English 或简体中文，选择后立即生效并持久保存。

计划保留本地日期与时区，记录保存绝对时间。全天事件按本地日历边界计算；夏令时的无效时刻拒绝保存，编辑既有记录而不改时间时保留其绝对时间。

## Windows debug 预览

工具、镜像和模拟器数据都放在忽略目录 `.tools` 中；Windows 使用现有 WHPX 加速。
窗口运行实际 debug APK。

```powershell
.\scripts\preview-debug.ps1
# 只更新并打开已经构建的 APK：
.\scripts\preview-debug.ps1 -SkipBuild
```

脚本优先使用工作区 `.tools/sdk`，其次读 `local.properties`；AVD 优先选 `chronota`，
否则用第一个可用 AVD，也可用 `-Avd <名字>` 指定。其他机器需先安装 Android Emulator、
Google APIs Android 35 x86_64 镜像，并准备对应 AVD；SDK/镜像不随源代码分发。

主页面没有固定标题栏，分类等独立页面保留返回与标题。表单按用途使用开关、输入框、选择列表和导航行；保存按钮居中着色。所有页面使用同一组紧凑尺寸和圆角；日程和日历采用连续底色，时间轴顶部使用淡出过渡，不使用重阴影。

底栏采样完整页面背景，使用 AndroidLiquidGlass 的开源 AGSL 镜片折射着色器，并加入轻微色散和边缘高光。Android 13+ 显示折射，Android 12 使用背景模糊，更早版本保留透明材质与边缘效果。弹窗统一为实心中央面板，每一层使用系统变暗遮罩，不使用跨窗口背景模糊。控件统一 48dp 高，底栏与悬浮球统一 56dp。光效和轻阴影限于悬浮元素。
实现依据：[AndroidLiquidGlass 折射实现](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt)、[Compose 绘制与 GraphicsLayer](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)。

## 工程结构

```text
app/src/main/java/app/chronota/
├── data/                  # Room entity/dao/db 与业务、计时、偏好、备份仓储
├── domain/               # 共享规则与类型
├── feature/
│   ├── browse/            # 计划与回顾共用的日程、日历
│   ├── today/
│   ├── todo/              # 计划、目标编辑
│   ├── review/
│   ├── timer/             # 计时面板、提醒调度和广播接收
│   ├── category/
│   └── settings/
└── ui/
    ├── components/
    └── theme/
```

不指定 fontFamily、不打包字体、不使用动态壁纸配色。
所有产品文案进入资源文件，所有页面复用 Design Token 与公共组件。
业务数据保存于 Room；DataStore 只用于偏好。当前 schema 为 11，已导出到
`app/schemas/app.chronota.data.db.AppDatabase`；1 → … → 11 的每一步都有非破坏
Migration 与迁移测试，既有数据全程保留。
旧库中曾有的关联字段仅为兼容保留，新记录不再写入关联，界面和统计也不使用关联。
禁止破坏性迁移；正式数据库不写入演示任务。首次启动本版本会补入默认分类
Sonata/学习、Nocturne/观影、Waltz/睡眠，已有同名分组会复用；观影预设类型、
平台 / 影院、评分、观后感四项选填属性。完成初始化后不会重新补回用户主动删除的默认分类。

图标来源为 [Lucide](https://github.com/lucide-icons/lucide)，许可随应用保存在 assets/licenses/lucide.txt。液态玻璃着色器改编自 Kyant 的 AndroidLiquidGlass，保留版权说明，Apache-2.0 许可位于 assets/licenses/backdrop.txt。

Snackbar Undo、从待规划面板拖出、无障碍完善属于后续迭代。
删除保留确认，拖动直接进入编辑器。WebDAV 凭据保存在应用私有目录，`allowBackup=false`
且系统云备份全面排除，不会被带出设备。

## 测试

`testDebugUnitTest` 共 131 个用例，覆盖 Room 关闭重开、分类级联删除范围、Plan/Record 独立性、
历史锁定、跨日与夏令时、重叠布局、计时暂停/恢复与幂等结束、番茄钟跨阶段恢复、
提醒重调度、属性事务校验、计划与记录独立统计、schema 1 → … → 11 迁移、全天事件、
时间轴创建限制与分类换组、循环展开与提醒跟随实例、目标周期与过期、备份全表往返与
恢复原子性，以及真实 MainActivity 的 CRUD 和计时重建流程。
另有导航、语言、主题、偏好恢复测试及带数据界面的宿主机截图。

多次写入的宿主机测试使用官方 Okio 存储适配器，以兼容 Windows 的文件替换语义；
正式应用和 Activity 测试仍使用 Android DataStore 默认实现。
Robolectric 首次执行会下载 Android 测试运行时。
它在 JVM 中运行，不能代替真机/模拟器的触摸、无障碍和系统栏检查。
测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。
Lint 报告：`app/build/reports/lint-results-debug.html`。
测试绘制的界面截图：`app/build/outputs/screenshots/`。

后续开发先阅读 [AGENTS.md](AGENTS.md) 与 [路线图](docs/ROADMAP.md)。
完整需求保存在 [PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md)，操作路线见 [Demo 导览](docs/DEMO.md)。
GitHub Actions 已配置构建、测试、Lint 和 APK/报告归档；需要推送到 GitHub 后才会运行。

## 版本依据

- [AGP 8.13 兼容表](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Navigation Compose](https://developer.android.com/develop/ui/compose/navigation)
