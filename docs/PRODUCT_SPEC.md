请开发一个可实际运行、可持续迭代的 Android 时间管理应用，项目名和应用名暂定为 **plan-record**。

## 1. 产品定位

plan-record 是一个以 **“计划（Plan）与实际记录（Record）”** 为核心的时间管理应用。

核心理念：

* Plan 表示“原本打算做什么”。
* Record 表示“实际上做了什么”。
* Plan 和 Record 必须始终是两个独立实体。
* Record 可以可选关联一个 Plan，但 Plan 不能因为“完成”而直接变成 Record。
* 用户既可以按照 Plan 执行，也可以记录完全没有提前计划的事情。
* Timer / Pomodoro 是产生 Record 的方式，而不是另一套独立的历史记录系统。
* 当前时间 NOW 是 Plan 与 Record 在 Today 页面中的重要语义边界：**未来主要用于计划，过去主要用于记录，现在用于执行。**

应用采用 local-first 思路，第一阶段不实现账号和云同步。

---

## 2. 技术路线

使用：

* Kotlin
* Jetpack Compose
* Room
* DataStore
* Navigation Compose
* Android 标准通知/提醒机制

Material 3 可以作为 Compose 基础设施使用，但不要依赖其默认视觉风格。

项目结构保持简单清晰，例如：

```text
app/
├── data/
│   ├── db/
│   ├── dao/
│   ├── entity/
│   └── repository/
├── domain/
├── feature/
│   ├── today/
│   ├── todo/
│   ├── review/
│   ├── category/
│   ├── timer/
│   └── settings/
└── ui/
    ├── components/
    └── theme/
```

不要一开始引入复杂 Clean Architecture、多模块、大量 Interface/UseCase 或其他不必要抽象。

但关键领域逻辑必须集中管理，不能散落在 Composable 中。

---

# 3. 一级页面

底部导航只保留三个一级页面：

### 今日 Today

负责执行。

核心是 Plan / Record 双栏时间轴以及悬浮球操作。

### 待办 Todo

负责管理所有 Plan。

### 回顾 Review

负责查看实际 Record、历史和统计。

分类管理、设置等均作为二级页面。

---

# 4. 核心数据模型

至少设计以下实体：

```text
Category
Plan
Record
TimerSession
Reminder
PropertyDefinition
PropertyValue
```

## Category

分类 UI 第一版使用两级结构，但数据库通过 `parentId` 保留扩展能力。

一级分类：

* 只有名称。
* 不设置颜色。
* 不设置图标。
* 主要负责组织二级分类。

二级分类：

* 名称
* parentId
* 用户自定义颜色
* 用户自定义图标
* sortOrder

Plan 和 Record 主要关联二级分类。

---

## Plan

至少包含：

```text
id
title
categoryId
scheduledDate?
startTime?
endTime?
estimatedDuration?
deadline?
note
createdAt
updatedAt
```

Plan 可以有三种基本安排形式：

```text
无日期
→ Inbox

有日期、无具体时间
→ 某天要做，但没有 time block

有日期、有 start/end
→ 已安排到 Timeline
```

不要简单使用 `completed=true` 来表达 Plan 的生命周期。

---

## Record

至少包含：

```text
id
title
categoryId
startTime
endTime
sourcePlanId?
note
createdBy
createdAt
```

其中：

```text
createdBy =
MANUAL
TIMER
POMODORO
```

Record 可以：

* 关联 Plan；
* 完全独立存在。

不得要求创建 Record 前必须存在 Plan。

---

# 5. Plan 的时间状态

统一实现 Plan 的动态时间状态：

```text
UNSCHEDULED
FUTURE
ACTIVE
PAST
```

例如：

```kotlin
fun Plan.temporalState(now: Instant): PlanTemporalState
```

所有页面必须调用同一套逻辑。

禁止 Today、Todo 等页面分别实现自己的时间判断。

---

# 6. Today 页面

Today 是应用最核心的页面，并且必须保持简洁。

页面主体是纵向时间轴：

```text
        Plan           Record

08:00
09:00   写论文          早餐
10:00                  写论文
11:00
────── NOW ────────────────
12:00
13:00   读文献
...
```

Plan 和 Record 左右两栏共用完全相同的时间坐标。

这样用户可以直接比较：

> 原本计划如何安排时间 / 实际如何使用时间。

Today 页面主要只有两种操作体系：

1. 时间轴直接操作
2. 悬浮球操作

不要加入大量常驻统计卡、工具栏和额外快捷按钮。

---

# 7. 时间轴交互

## 空白区域

长按 Timeline 空白区域创建事项。

Plan 栏空白：

```text
长按
→ 新建 Plan
```

Record 栏空白：

```text
长按
→ 新建 Record
```

也可以提供轻量菜单：

```text
添加计划
添加记录
```

根据用户长按位置自动计算默认时间，并吸附到例如：

```text
15 min
30 min
```

之后打开 Bottom Sheet 精确设置。

---

## 已有事项

点按已有 Plan：

```text
打开 Plan 详情/编辑
```

点按已有 Record：

```text
打开 Record 详情/编辑
```

点按行为应保持稳定，不因为 Plan 时间状态改变。

---

# 8. 未来 Plan

当：

```text
PlanTemporalState == FUTURE
```

长按 Plan：

```text
进入拖动
→ 上下移动时间块
→ 时间吸附
→ 松手确认新时间
```

后续支持拖动 Plan 上下边缘调整时长。

第一版可以先实现整体移动。

允许 Plan 之间时间重叠，不在数据层禁止。

---

# 9. 历史 Plan

默认使用严格历史模式。

当：

```text
PlanTemporalState == PAST
```

历史 Plan 应被冻结，保留“当时原本如何计划”的信息。

严格模式下：

```text
长按历史 Plan
→ 添加对应 Record
```

新 Record 默认继承：

```text
title
category
startTime
endTime
sourcePlanId
```

用户随后修改真实发生时间。

---

# 10. 宽松历史模式

设置中提供一个全局选项：

**允许修改历史计划**

内部建议使用：

```text
HistoricalPlanPolicy.IMMUTABLE
HistoricalPlanPolicy.EDITABLE
```

而不是含义模糊的 `relaxedMode`。

开启后：

* 历史 Plan 可以像未来 Plan 一样编辑、移动。
* 可以调整历史 Plan 时间。

但必须保持：

```text
Plan != Record
```

修改历史 Plan：

* 不自动修改 Record；
* 不自动生成 Record；
* 不改变已有 Record。

---

# 11. ACTIVE Plan

当当前时间位于 Plan 的 start/end 之间：

```text
PlanTemporalState == ACTIVE
```

长按时不要直接整体移动。

优先提供：

```text
开始记录
调整剩余时间
查看详情
```

后续可以扩展：

```text
延后剩余部分
跳过
```

---

# 12. NOW 时间线

Today 页面显示当前时间线 NOW。

NOW：

* 横跨 Plan / Record 两栏；
* 每分钟更新即可；
* 页面进入时自动滚动到当前时间附近；
* 是过去与未来的重要视觉边界。

过去 Plan 可以适度弱化显示，未来 Plan 正常显示。

---

# 13. 悬浮球

Today 页面右下角有一个小型圆形悬浮球。

悬浮球有两套操作。

## 点按

用户可以在设置中自定义默认动作：

```text
开始正计时
开始番茄钟
新建 Plan
新建 Record
打开待规划列表
```

默认建议：

```text
开始正计时
```

---

## 长按

长按悬浮球展开 radial menu。

第一版建议四个核心方向：

```text
             新计划

待规划列表      ●       新记录

             开始计时
```

也可以增加番茄钟作为第五项。

第一阶段可以：

```text
长按
→ 展开
→ 点击动作
```

之后实现真正 marking menu：

```text
长按
→ 保持按住
→ 向某方向滑动
→ 项目高亮
→ 松手执行
```

提供适度 haptic feedback。

---

# 14. 待规划列表

通过悬浮球 radial menu 打开 Bottom Sheet。

显示：

* Inbox
* 今天但没有具体时间的 Plan
* 临近 deadline 的 Plan

第一版：

```text
点击 Plan
→ 选择时间
→ 安排到 Timeline
```

后续实现：

```text
长按 Plan
→ 从 Bottom Sheet 拖出
→ 拖入 Timeline
→ 根据位置估计时间
→ 松手
→ 精确确认 start/end
```

统一调用：

```text
schedulePlan(planId, start, end)
```

不要为拖拽单独实现数据库保存逻辑。

---

# 15. Todo 页面

负责 Plan 的完整管理。

至少包含：

```text
Inbox
Today
Upcoming
Categories
```

支持：

* 新建
* 编辑
* 删除
* 分类
* 日期
* 起止时间
* 预计时长
* deadline
* note
* reminder

Todo 页面偏列表式管理；Today 页面偏 Timeline 执行。

---

# 16. Plan Reminder

Plan 支持提醒通知。

允许设置：

```text
开始前 5 min
开始前 15 min
开始前 30 min
开始前 1 h
自定义
```

也允许围绕 deadline 设置提醒。

一个 Plan 可以拥有一个或多个 Reminder。

提醒应：

* 使用 Android 合适的通知/后台机制；
* 修改 Plan 时间后重新调度；
* 删除 Plan 后取消；
* App 重启后仍然正确；
* 设备重启后能够恢复；
* 正确申请和处理 Android 通知权限。

通知调度逻辑独立于 UI。

---

# 17. 正计时器

支持：

```text
开始
暂停
恢复
结束
```

可以：

* 关联 Plan；
* 选择分类；
* 不关联任何 Plan。

结束后生成 Record。

不要通过每秒：

```text
elapsed++
```

计算时间。

应保存：

```text
startTimestamp
pausedDuration
```

并使用：

```text
elapsed = now - startTimestamp - pausedDuration
```

确保：

* App 后台运行正常；
* Activity 重建正常；
* App 被杀后可以恢复计时状态。

---

# 18. Pomodoro

正计时稳定后加入。

支持：

```text
工作时间
休息时间
循环次数
```

工作阶段产生 Record。

休息是否记录可以后续设置。

---

# 19. 悬浮球计时状态

悬浮球可以根据状态变化：

```text
IDLE
TIMING
POMODORO
```

计时时可以在悬浮球附近简洁显示 elapsed time。

点击计时中的悬浮球：

```text
暂停
继续
结束
```

不要额外占据 Today 页面大量空间。

---

# 20. 自定义属性

Record 支持按照二级 Category 定义自定义属性。

使用：

```text
PropertyDefinition
PropertyValue
```

不要把所有属性直接塞进 Record 的单个 JSON 字段。

第一版支持：

```text
Text
Number
Boolean
Select
MultiSelect
Rating
```

例如：

```text
科研 / 模拟

N
Temperature
Machine
```

创建 Record 时，根据其二级分类动态生成表单。

Plan 第一版不需要自定义属性。

---

# 21. Review 页面

Review 主要分析 Record。

支持：

```text
日
周
月
自定义时间范围
```

第一阶段实现：

* Record 历史列表
* 总记录时长
* 一级分类统计
* 二级分类统计
* 基础时间趋势

之后实现 Plan vs Actual：

```text
计划总时长
实际总时长
计划且有执行
计划但无 Record
无 Plan 的 Record
```

Plan / Record 对应关系优先使用：

```text
sourcePlanId
```

禁止通过标题字符串匹配。

可以动态推导：

```text
PLANNED_ONLY
RECORDED
PARTIALLY_RECORDED
UNPLANNED_RECORD
```

---

# 22. Timeline 特殊情况

支持：

### 时间重叠

Plan / Record 都允许重叠。

UI 可通过：

```text
缩窄
并排
```

展示重叠项目。

### 跨日

例如：

```text
23:00–01:00
```

数据库仍然保存一个 Record。

Timeline 只裁切显示当天部分。

禁止为了显示方便把它拆成两条 Record。

---

# 23. 分类显示

一级分类：

```text
纯文本
```

二级分类：

```text
图标
颜色
名称
```

时间轴和列表中的任务主要显示：

```text
[二级分类图标] 任务名称
```

颜色作为辅助识别信息。

一级分类主要用于组织，不在任务卡片中抢占视觉层级。

---

# 24. UI / Design System

整体风格要求：

* 简洁、克制、紧凑；
* 可以参考 Apple Calendar 的信息组织和视觉克制度，但不要复制 iOS 控件；
* 高信息密度；
* 少 Card；
* 少阴影；
* 少大面积彩色背景；
* 主要依靠排版、留白、细分隔线和对齐建立层级；
* 二级分类颜色是界面主要彩色信息来源；
* 图标采用统一、圆润、简洁的 outline 风格；
* 圆角统一，建立少量全局 radius token；
* 避免大量 pill shape 和 Material 3 式 oversized controls。

字体完全使用 Android 系统默认字体：

* 不设置自定义 fontFamily；
* 不打包字体；
* 只控制字号、字重和行高。

建立统一 Design Token 和公共组件，不允许各页面随意定义颜色、圆角和间距。

---

# 25. 深色 / 浅色模式

完整支持：

```text
跟随系统
浅色
深色
```

从项目初期同时实现。

浅色：

* 白色/近白中性背景。

深色：

* 黑色/近黑中性背景。

分类颜色需要在两种背景下保持可识别性。

---

# 26. 多语言

从第一版建立完整 i18n 接口。

所有用户可见字符串必须放在 Android string resources 中。

禁止在 Composable 中硬编码中文/英文 UI 文本。

至少建立：

```text
values/strings.xml
values-zh-rCN/strings.xml
```

第一版支持：

* 简体中文
* English

后续应容易增加其他语言。

---

# 27. DataStore

DataStore 保存应用偏好，例如：

```text
ThemeMode
HistoricalPlanPolicy
FloatingOrbDefaultAction
DefaultTimerMode
TimelineSnapInterval
```

不要把业务数据存入 DataStore。

---

# 28. Room

Room 保存所有业务数据：

```text
Category
Plan
Record
TimerSession
Reminder
PropertyDefinition
PropertyValue
```

从第一个正式 schema 开始维护 Migration。

禁止使用 destructive migration。

不得为了修复数据库问题直接清空用户数据库。

数据库 schema 修改应被视为需要谨慎处理的操作。

---

# 29. 时间表示

认真区分：

```text
Instant
LocalDate
LocalTime
Duration
```

Record 实际发生时间优先保存绝对 timestamp。

Plan 的本地日期/时间保留相应日历语义。

不要把所有时间统一存成任意字符串。

正确考虑：

* 时区变化
* DST
* 跨日

---

# 30. Undo 和误操作保护

对高风险操作提供 Undo 或确认。

例如：

```text
删除 Plan
删除 Record
移动 Plan
调整时间
```

优先使用轻量 Snackbar Undo。

历史数据删除等不可逆操作可以要求二次确认。

---

# 31. Haptic Feedback

适度使用震动反馈：

* 长按成功；
* 开始拖动；
* 时间吸附；
* radial menu 项目切换；
* 松手确认。

不要过度震动。

---

# 32. 数据导出与备份

后期支持：

```text
JSON
CSV
SQLite 完整备份
```

JSON：

* 数据迁移
* 可读备份

CSV：

* 数据分析

SQLite：

* 完整恢复

导入时：

* 校验 schema/version；
* 避免重复数据；
* 导入前备份当前数据；
* 不允许无提示直接覆盖。

---

# 33. 测试

优先为核心领域规则编写自动化测试：

* Plan temporalState
* FUTURE / ACTIVE / PAST 边界
* HistoricalPlanPolicy
* Plan → Record 草稿
* Timer 恢复
* Reminder 调度
* 跨日 duration
* DST / timezone
* Category 删除规则
* Room migration

UI 测试只覆盖关键流程。

---

# 34. AGENTS.md

在项目根目录创建 `AGENTS.md`，至少写入以下不可违反的规则：

```text
1. Plan and Record are separate domain entities.
2. A Plan never becomes a Record.
3. A Record may optionally reference a Plan.
4. Records may exist without Plans.
5. Timer and Pomodoro sessions ultimately produce Records.
6. Historical Plan behavior is controlled by HistoricalPlanPolicy.
7. Temporal state must be calculated by centralized domain logic.
8. UI code must not independently implement temporal-state rules.
9. Database schema changes require explicit consideration and migration.
10. Never use destructive Room migration for user data.
11. Business data belongs in Room.
12. User preferences belong in DataStore.
13. Today is timeline-first. Avoid adding unnecessary permanent UI elements.
14. Equivalent actions from different gestures must reuse the same domain/repository logic.
15. Do not implement persistence directly inside Composables.
16. Do not hard-code user-visible strings.
17. UI styling must use the shared plan-record Design System.
```

---

# 35. 实现顺序

不要试图一次性完成全部功能。

按照以下里程碑开发，每一步都必须保持项目可编译、可运行：

```text
M0
项目骨架
主题
Design System
i18n
Navigation
AGENTS.md

M1
Room / DataStore
核心数据模型
Category

M2
Plan CRUD
Todo 页面
Reminder 基础模型

M3
Record CRUD
Plan / Record 关联

M4
Today 双栏 Timeline
时间坐标
NOW line

M5
FUTURE / ACTIVE / PAST
时间相关交互
严格/宽松历史模式

M6
悬浮球
Radial Menu

M7
Timer
Timer 状态恢复

M8
Pomodoro
通知与 Plan Reminder 完整实现

M9
待规划 Bottom Sheet
Plan → Timeline
拖拽安排

M10
PropertyDefinition
PropertyValue
动态 Record 表单

M11
Review
分类统计
Plan vs Actual

M12
备份 / 导入导出
Undo
异常情况处理
无障碍
测试
UI 打磨
```

在每个 Milestone 完成后：

1. 确保项目编译成功；
2. 运行相关测试；
3. 确保已有功能没有回归；
4. 总结本阶段实现内容；
5. 再进入下一阶段。

优先实现正确的数据模型和业务行为，再打磨复杂动画和视觉效果。Timeline 拖拽、radial marking menu 等复杂交互可以先实现简单可用版本，再逐步增强，不要为了动画效果破坏领域模型或数据层。
