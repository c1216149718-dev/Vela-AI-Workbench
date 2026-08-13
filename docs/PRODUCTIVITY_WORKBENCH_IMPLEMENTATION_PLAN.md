# AI 生产力工作台实施方案

状态：**Approved for implementation**  
基线版本：`1.4.0`（versionCode `7`）  
适用目录：`D:\CodexProjects\DeepSeekWidget`  
更新时间：2026-08-04（Asia/Shanghai）  
目标读者：后续负责编码、测试和交付的 agent

本文档是工作台功能的执行依据。除非用户明确改变需求，后续 agent 不需要重新讨论产品定位、页面结构、数据模型或技术路线，应按阶段依次实现。`docs/LUMA_ARCHITECTURE.md` 保留为架构参考，但其中一次性全量重构的迁移顺序由本文档取代。

## 1. 产品目标与边界

### 1.1 产品定位

将现有的 AI 余额和用量查看工具，增量扩展为一个本地优先的个人 AI 生产力工作台。它围绕一个闭环组织功能：

```text
计划任务 -> 进入专注 -> 使用 AI -> 查看时间与费用 -> 每日/周期复盘
```

应用每次打开应优先回答三个问题：

1. 今天最重要的事情是什么？
2. 当前完成和专注进度如何？
3. DeepSeek 与 APIKEY.FUN 的资源还剩多少、近期消耗如何？

### 1.2 成功标准

- 用户能在 10 秒内新增一项今日任务并开始专注。
- 任务、专注记录、复盘数据在离线状态下仍可查看和编辑。
- 工作台能稳定展示两个 AI 服务的余额摘要和近期消耗，不重复实现供应商网络逻辑。
- APIKEY.FUN 能聚合用户主动配置的多个 API Key 的模型用量；余额只显示指定主 Key，避免重复累计同一账户余额。
- DeepSeek 历史消耗始终标注为“余额扣减估算”，不伪装成官方 Token、请求或模型明细。
- 现有双余额桌面小组件继续稳定工作，工作台开发不能破坏其 RemoteViews、刷新和缓存路径。

### 1.3 本路线明确不做

- 不迁移到 Compose，不拆分 Gradle 多模块，不引入 Hilt。
- 不做云同步、账号系统、团队协作和跨设备同步。
- 不自动声称某项任务消耗了多少 AI 费用。现有供应商接口没有任务身份，报告只并列展示生产力与 AI 消耗。
- 不申请精确闹钟权限；提醒和专注结束通知允许系统做省电调度。
- 不在本路线新增第二个桌面小组件；现有小组件继续只显示两个服务的余额和状态。
- 不抓取没有稳定公开契约的网站页面，不把 GitHub Trending 网页爬虫作为核心能力。
- 不在 1.5.0 之前做全量 LUMA 分层重写。

## 2. 已锁定的产品决策

### 2.1 旧模块重新归并

当前六个占位模块不是六套独立数据和页面，按下表收敛：

| 旧 ID | 新归属 | 执行决定 |
| --- | --- | --- |
| `daily_plan` | 今日工作 | 今日日期下的任务投影视图，不建立独立计划表 |
| `todo` | 任务 | 与每日计划共用同一个 `tasks` 表 |
| `pomodoro` | 专注 | 更名为“专注”，支持自定义时长，不局限 25 分钟 |
| `alarm` | 提醒能力 | 附着在任务和专注上，不保留顶层模块卡片 |
| `hot_news` | 信息收件箱 | 固定使用 Hacker News 公共 API 的技术热点源 |
| `github_trending` | 信息收件箱 | 固定使用 GitHub Search API 的新锐仓库源 |

工作台最终只显示四个可进入的工作域：

- 任务
- 专注
- 效率报告
- 信息收件箱

### 2.2 导航与启动页

- 保持单 Activity + Fragment + XML View + ViewBinding。
- 在阶段 1 引入 AndroidX Navigation Component，替代 `MainActivity` 手动 `replace()`。
- 根级底栏顺序在阶段 2 调整为：`工作台 / DeepSeek / APIKEY.FUN / 设置`。
- 阶段 2 起，冷启动默认进入工作台；应用在进程内保留各根页面状态。
- 从桌面小组件点击余额区域时，通过 Intent extra `open_destination` 直接进入对应供应商页；点击非供应商区域进入工作台。
- 工作台详情页仍由同一 `NavHostFragment` 承载，进入详情时隐藏底栏，返回根页面时恢复。

### 2.3 数据和架构

- `Preferences DataStore`：继续保存设置、API Key、账户缓存、刷新间隔和预算配置。
- `Room`：保存任务、项目、专注、复盘、归一化 AI 用量缓存和信息收件箱。
- 业务页面采用 `ViewModel + StateFlow + Repository`。只有跨多个 Repository 的流程才增加 UseCase，禁止为简单 CRUD 建空壳 UseCase。
- 使用轻量 `AppContainer` 手动装配依赖，不引入 Hilt/Koin。
- 网络客户端继续使用 OkHttp 和 kotlinx.serialization，供应商解析器保持相互独立。
- 数据库必须 `exportSchema = true`，所有版本迁移必须显式实现并测试，禁止 `fallbackToDestructiveMigration()`。

### 2.4 AI 用量口径

- DeepSeek：余额快照相邻扣减的本地估算；费用可统计，请求数、Token、模型分布保持未知。
- APIKEY.FUN：按当前 API Key ID 返回的官方接口数据。配置多个 Key 后，本地按日期和模型求和。
- APIKEY.FUN 多 Key 余额不能相加。用户必须指定一个“余额主 Key”，桌面小组件和总览只显示该 Key 的余额。
- 重复 Key 按 SHA-256 指纹拒绝保存，UI 和日志不展示原始 Key。
- 一次聚合允许部分成功：成功 Key 的数据正常展示，失败 Key 以别名列在“部分数据未更新”状态中。

## 3. 信息架构和页面规格

### 3.1 工作台首页

页面由一个纵向 `RecyclerView` 承载，使用多 ViewType，禁止 ScrollView 内嵌多个不可控 RecyclerView。显示顺序固定：

1. **日期与问候**：本地日期、星期；不显示营销文案。
2. **快速新增**：单行输入框、添加图标和“加入今天”开关；回车或按钮立即创建任务。
3. **今天**：最多显示 3 项未完成任务，按 `priority DESC, sortOrder ASC`；可勾选完成，底部进入全部任务。
4. **当前专注**：无会话时显示“开始专注”；有会话时显示任务名、剩余时间、暂停/继续和结束。
5. **AI 资源**：DeepSeek 与 APIKEY.FUN 两行摘要，包含余额、今日消耗、7 日消耗、同步状态；点击进入对应供应商页。
6. **工作域**：2 列固定网格，入口为任务、专注、效率报告、信息收件箱。
7. **今日复盘**：完成任务数、专注分钟数和 1 至 5 分评价；未填写时显示“写复盘”。

状态要求：

- 首次使用：保留快速新增和清晰空状态，不展示“敬请期待”。
- 加载：只在对应区块使用固定尺寸占位，不遮盖整页。
- 错误：显示上次成功缓存和错误状态，刷新按钮可重试。
- 离线：任务和专注全部可用；AI 摘要和信息流显示缓存时间。

### 3.2 任务页

顶部为标题、搜索和筛选菜单。筛选项固定为：`今天 / 计划中 / 全部 / 已完成`。列表使用 `ListAdapter + DiffUtil`。

任务创建/编辑字段：

- 标题：必填，1 至 120 字符。
- 备注：可选，最多 4000 字符。
- 项目：可选，默认“收件箱”。
- 优先级：无、低、中、高。
- 计划日期：可选；快捷项为今天、明天、清除。
- 截止时间：可选。
- 提醒时间：可选，不得晚于任务完成后继续保留。
- 预计分钟：可选，1 至 1440。

交互：

- 左侧复选框完成/恢复任务。
- 点击正文进入编辑页。
- 长按进入选择模式；删除必须二次确认，并同步取消提醒。
- 今日列表支持拖动排序，只更新同一天任务的 `sortOrder`。
- 完成任务时写入 `completedAt`；恢复时清空。
- 恢复已完成任务时，有 `plannedDate` 的任务回到 `PLANNED`，否则回到 `BACKLOG`。
- 关联任务开始专注时，未完成任务改为 `IN_PROGRESS`；专注结束或取消后保持 `IN_PROGRESS`，是否完成由用户确认。
- 快速新增失败时保留输入内容并显示字段错误。

### 3.3 专注页

预设时长为 `15 / 25 / 45 / 60` 分钟，另有自定义分钟输入。可选择关联任务，也允许无任务专注。

状态机固定为：

```text
IDLE -> RUNNING -> PAUSED -> RUNNING -> COMPLETED
                         \-> CANCELLED
RUNNING -----------------> CANCELLED
```

实现规则：

- 剩余时间从持久化时间戳计算，不能每秒递减后把内存值当真值。
- `RUNNING` 剩余时间：`max(0, expectedEndAt - now)`。
- `PAUSED` 剩余时间：`max(0, expectedEndAt - pausedAt)`。
- 暂停时记录 `pausedAt`；继续时将本次暂停时长加入 `accumulatedPauseMillis`，并把 `expectedEndAt` 顺延相同长度。
- 前台每秒刷新显示；进程重建后从 Room 恢复。
- 开始和继续时安排唯一的 `OneTimeWorkRequest`；暂停、取消或完成时取消。
- 系统延迟通知时，打开页面后必须依据真实时间立即归正状态。
- 完成时允许一键标记关联任务完成，但默认不自动完成任务。

专注历史按日分组，显示总分钟、完成会话数、取消会话数。取消会话保留记录，但不计入有效专注分钟。

### 3.4 效率报告页

时间范围固定为 `1 / 3 / 5 / 7 / 14 / 28 / 30` 天，沿用现有用量页逻辑。报告有三个页签：

1. **概览**：完成任务、计划任务、完成率、专注分钟、会话数、逾期任务、AI 总费用。
2. **AI 消耗**：DeepSeek 估算费用、APIKEY.FUN 实际费用、请求、Token、模型排行和与上一等长周期对比。
3. **节奏**：每日完成任务柱状图与每日专注折线图，同图按日期对齐；下方显示每日复盘评价。

显示原则：

- DeepSeek 卡片和图例始终带“估算”标签。
- 未知指标显示 `--`，不得显示为 `0`。
- 不同币种分别统计和显示，禁止在没有汇率来源时直接相加或换算。
- 不生成“每个任务花费多少 AI”或“AI 投资回报率”等无证据指标。
- 图表必须附带可读文本摘要，例如“7 天专注 185 分钟，较上一周期增加 12%”。

### 3.5 信息收件箱

页面包含 `技术热点 / GitHub 新锐 / 已收藏` 三个页签。

数据源固定：

- 技术热点：Hacker News 官方 Firebase API。先取 `/v0/topstories.json`，再以最多 6 个并发请求读取前 30 个 `/v0/item/{id}.json`。
- GitHub 新锐：GitHub Repository Search，查询最近 7 天新建仓库，按 stars 降序，`per_page=30`。无 Token 时使用公开限额；设置页可选配置 GitHub Token。

列表项包含来源、标题、摘要元信息、热度、发布时间、收藏按钮和外部打开按钮。点击收藏写入本地；“转为任务”创建 `sourceType` 和 `sourceUrl` 已填充的任务。

缓存与失败策略：

- 列表缓存 6 小时，手动下拉允许强制刷新。
- 网络失败时展示缓存；无缓存时显示带重试按钮的错误状态。
- Hacker News 返回的 HTML 文本必须转为纯文本后显示。
- 外部 URL 只允许 `http` 和 `https`，交给系统浏览器打开。
- GitHub 限流时显示重置时间，不做高频自动重试。

### 3.6 设置页新增内容

在现有刷新设置基础上分为：

- **AI 账户**：DeepSeek Key；APIKEY.FUN Key 列表、别名、测试连接、余额主 Key；可选 GitHub Token。
- **预算提醒**：DeepSeek 每日/每周预算；APIKEY.FUN 每日/每周预算；阈值为 80% 和 100%。
- **专注与提醒**：默认专注时长、声音/振动开关、通知权限状态。
- **数据**：导出 JSON、导入 JSON、清除本地生产力数据。
- **关于**：版本和数据口径说明。

Key 输入框默认遮盖；仅允许用户主动按住查看，离开页面立即恢复遮盖。

### 3.7 结构线框

工作台首页按以下结构实现，不增加顶部营销 Hero，不把整个页面包成一张大卡：

```text
┌──────────────────────────────────┐
│ 8 月 4 日 · 星期二        [设置] │
│ 早上好                           │
├──────────────────────────────────┤
│ [输入一项任务……          ] [+]  │
│ [✓ 加入今天]                    │
├──────────────────────────────────┤
│ 今天                       2 / 5 │
│ ○ 完成报告                      │
│ ○ 修复接口                高    │
│ ○ 整理资料                      │
│ 查看全部                         │
├──────────────────────────────────┤
│ 当前专注                         │
│ 修复接口            18:42        │
│                  [暂停] [结束]   │
├──────────────────────────────────┤
│ AI 资源                           │
│ DeepSeek      ¥42.80   今日 ¥1.2 │
│ APIKEY.FUN    $18.30   7日 $6.4  │
├──────────────────────────────────┤
│ [任务] [专注]                    │
│ [报告] [信息收件箱]              │
├──────────────────────────────────┤
│ 今日复盘  完成 2 · 专注 65 分钟 │
│ [1] [2] [3] [4] [5]    [写复盘] │
└──────────────────────────────────┘
```

无活动专注时，该区块只显示默认时长、可选任务和“开始”按钮；AI 数据没有缓存时分别显示“去配置”，不能让整个区块消失。

## 4. 视觉与动效规范

### 4.1 Claude 风格视觉令牌

现有暖色方向保留，但降低大面积米色的单一感。阶段 2 统一为：

| 语义 | 色值 |
| --- | --- |
| 页面背景 | `#F5F4F0` |
| 主表面 | `#FFFFFF` |
| 次表面 | `#ECEAE4` |
| 主文字 | `#2F2B28` |
| 次文字 | `#6F6963` |
| 边框 | `#DDD9D2` |
| Claude 珊瑚强调 | `#D97757` |
| DeepSeek 灰蓝 | `#4A6A7A` |
| APIKEY.FUN 琥珀 | `#A0653A` |
| 成功 | `#3A7D5D` |
| 警告 | `#B77932` |
| 错误 | `#B94A3D` |

- 页面标题可使用系统 serif 字体；正文、数字、按钮全部使用系统 sans。
- 卡片圆角统一 `8dp`，边框 `1dp`，不使用渐变、发光、装饰圆球或嵌套卡片。
- 触控目标不小于 `48dp`；图标按钮使用项目现有矢量图标或 Material/Lucide 等价图标，并提供 `contentDescription`。
- 数字使用等宽数字特性可用的系统字体，金额不因位数变化造成布局跳动。
- 2 列模块卡片使用固定最小高度和约束宽度，最长中文文案允许换两行。

### 4.2 动效

- 页面进入：只做 `180ms` 透明度和 `8dp` 位移，不对整棵视图做长动画。
- 用量趋势：数据成功加载后以 `420ms` 绘制进度从 0 到 1。
- 模型排行：条形图以 `40ms` stagger、总时长不超过 `480ms`。
- 指标数字：旧值到新值 `300ms` 插值；首次加载从 0 开始，但未知值直接显示 `--`。
- 专注计时：仅秒数变化；不做持续缩放。完成时使用一次 `220ms` 强调。
- `ValueAnimator.areAnimatorsEnabled()` 为 false 时立即显示最终状态，功能信息不能依赖动画。

### 4.3 启动图标和名称

- 阶段 2 将应用显示名称改为“AI 工作台”，`applicationId` 保持 `com.deepseek.widget`。
- 自适应图标使用暖白背景、深灰四格工作台轮廓和单个珊瑚色进度短条；无文字、无渐变、无阴影细节。
- monochrome 图标只保留四格轮廓，确保 Android 主题图标清晰。

## 5. 技术实现规格

### 5.1 依赖版本

为兼容当前 AGP `8.2.2`、Kotlin `1.9.22`、Gradle `8.5`，本路线固定使用以下版本，不在功能开发中顺带升级工具链：

```kotlin
// root build.gradle.kts
id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false

// app/build.gradle.kts
implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

testImplementation("androidx.arch.core:core-testing:2.2.0")
androidTestImplementation("androidx.room:room-testing:2.6.1")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.work:work-testing:2.9.0")
```

Room 官方文档当前已进入 3.x，但该版本要求新的 `androidx.room3` 依赖和 KSP；本项目先使用与现有 Kotlin/AGP 风险更低的 2.6.1，工具链升级应单独立项，不与工作台功能混合。

### 5.2 包结构

新增代码使用以下结构，不移动现有供应商代码，直到对应阶段验证完成：

```text
com.deepseek.widget
├─ DeepSeekWidgetApp.kt
├─ di/
│  └─ AppContainer.kt
├─ data/
│  ├─ local/
│  │  ├─ WorkbenchDatabase.kt
│  │  ├─ Converters.kt
│  │  ├─ entity/
│  │  └─ dao/
│  ├─ repository/
│  └─ remote/
│     ├─ github/
│     └─ hackernews/
├─ domain/model/
├─ feature/
│  ├─ workbench/
│  ├─ tasks/
│  ├─ focus/
│  ├─ reports/
│  └─ inbox/
├─ navigation/
│  └─ Destination.kt
└─ worker/
   ├─ TaskReminderWorker.kt
   ├─ FocusCompletionWorker.kt
   └─ UsageSyncWorker.kt
```

现有 `DeepSeekFragment`、`ApiKeyFunFragment`、`SettingsFragment` 和小组件类保留原包路径，阶段 3 再逐步接入共享 Repository。

### 5.3 AppContainer

新增 `DeepSeekWidgetApp : Application`，在 Manifest 注册。`AppContainer` 为进程内单例，持有：

- `WorkbenchDatabase`
- `AppPreferences`
- `DeepSeekApiClient`
- 五个 Repository 实现
- `WorkManager` 调度器包装类

Fragment 通过 `requireActivity().application as DeepSeekWidgetApp` 获取容器并使用自定义 `ViewModelProvider.Factory`。禁止在 Fragment 内直接 new 数据库、DAO 或网络客户端。

### 5.4 数据库版本

#### Room v1：生产力核心

`ProjectEntity`：

```text
id Long PK auto
name String(1..60)
colorArgb Int
archived Boolean default false
createdAt Long
updatedAt Long
```

首次创建数据库时插入 id 固定为 `1`、名称为“收件箱”的默认项目；不得删除，只能重命名。

`TaskEntity`：

```text
id Long PK auto
title String
notes String default ""
projectId Long FK projects.id ON DELETE SET NULL
status String: BACKLOG | PLANNED | IN_PROGRESS | DONE | CANCELLED
priority Int: 0..3
plannedDate String? ISO-8601 YYYY-MM-DD
dueAt Long?
reminderAt Long?
estimateMinutes Int?
sortOrder Long
sourceType String: MANUAL | HACKER_NEWS | GITHUB
sourceUrl String?
createdAt Long
updatedAt Long
completedAt Long?
```

索引：`projectId`、`plannedDate`、`status`、`dueAt`。

`FocusSessionEntity`：

```text
id Long PK auto
taskId Long? FK tasks.id ON DELETE SET NULL
plannedMinutes Int
startedAt Long
expectedEndAt Long
endedAt Long?
pausedAt Long?
accumulatedPauseMillis Long default 0
status String: RUNNING | PAUSED | COMPLETED | CANCELLED
createdAt Long
updatedAt Long
```

数据库只允许一个 `RUNNING` 或 `PAUSED` 会话。Repository 开始会话前必须在事务中结束或拒绝冲突会话。

`DailyReviewEntity`：

```text
date String PK ISO-8601 YYYY-MM-DD
rating Int? 1..5
note String default ""
createdAt Long
updatedAt Long
```

#### Room v2：AI 用量缓存

新增 `AiUsageDailyEntity`：

```text
provider String: DEEPSEEK | APIKEY_FUN
credentialId String
date String ISO-8601 YYYY-MM-DD
model String
currency String
cost Decimal-as-String
requests Long?
inputTokens Long?
outputTokens Long?
totalTokens Long?
isEstimated Boolean
updatedAt Long
PK(provider, credentialId, date, model)
```

金额禁止用 `Double` 持久化；以规范化十进制字符串保存，Repository 中使用 `BigDecimal`。

#### Room v3：信息收件箱

新增 `ResourceItemEntity`，同时承担 6 小时列表缓存和永久收藏，避免同一外部条目存在两份本地记录：

```text
id Long PK auto
externalId String
source String: HACKER_NEWS | GITHUB
title String
url String
summary String
author String?
score Long?
publishedAt Long?
fetchedAt Long
expiresAt Long
isSaved Boolean default false
savedAt Long?
convertedTaskId Long?
UNIQUE(source, externalId)
```

缓存清理只删除 `isSaved=false AND expiresAt < now` 的记录；收藏项即使过期也保留，并可在下次联网时更新元信息。

### 5.5 DAO 最低接口

`TaskDao`：

- `observeTasks(filter...) : Flow<List<TaskEntity>>`
- `observeToday(date) : Flow<List<TaskEntity>>`
- `observeTask(id) : Flow<TaskEntity?>`
- `insert/update/delete`
- `complete(id, completedAt, updatedAt)`
- `restore(id, status, updatedAt)`
- `updateSortOrders(List<TaskOrderUpdate>)`，必须 `@Transaction`
- `countPlanned/countCompleted/countOverdue` 按日期范围统计

`FocusSessionDao`：

- `observeActive() : Flow<FocusSessionEntity?>`
- `observeHistory(start, end) : Flow<List<FocusSessionEntity>>`
- `insert/update`
- `complete/cancel/pause/resume` 使用条件更新，防止旧 UI 状态覆盖新状态
- `sumCompletedMinutes(start, end)`

`DailyReviewDao`、`AiUsageDao`、`ResourceItemDao` 分别提供按日期范围或来源的 `Flow` 查询、upsert 和清理接口。

### 5.6 Repository

- `TaskRepository`：任务 CRUD、今日投影、排序、提醒调度。
- `FocusRepository`：状态机、真实剩余时间计算、结束通知调度。
- `ReviewRepository`：每日复盘 upsert 和周期查询。
- `UsageInsightsRepository`：复用现有两个供应商客户端，归一化缓存，聚合多 Key，输出报告。
- `ResourceInboxRepository`：Hacker News/GitHub 拉取、6 小时缓存、收藏和转任务。

所有 Repository 对 UI 返回领域模型或明确的结果类型，不把 Room Entity、OkHttp Response、JSON 节点直接暴露给 Fragment。

### 5.7 UI 状态规范

每个 ViewModel 暴露一个不可变 `StateFlow<UiState>`。`UiState` 至少包含：

```text
isInitialLoading
isRefreshing
content
emptyReason
errorMessage
lastUpdatedAt
```

一次性事件（导航、Snackbar、权限请求）使用有消费语义的 `Channel`/`SharedFlow`，不得把 Toast 文本放进长期 StateFlow 导致旋转屏幕重复显示。

### 5.8 工作台注册表契约

阶段 2 用真实路由替代 `enabled` 占位标记：

```kotlin
enum class WorkbenchModuleId { TASKS, FOCUS, REPORTS, INBOX }

data class WorkbenchModule(
    val id: WorkbenchModuleId,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val accentColorRes: Int,
    @IdRes val destinationId: Int,
    val sortOrder: Int
)
```

模块的动态摘要和徽标由 `WorkbenchViewModel` 以 `Map<WorkbenchModuleId, ModuleSummary>` 提供，Registry 不持有 Context、Repository 或 suspend 函数。点击模块始终按 `destinationId` 导航，不再保留 Toast 占位分支。

### 5.9 路由和资源命名

`res/navigation/main_nav_graph.xml` 固定使用以下 destination ID：

| ID | Fragment | 参数 |
| --- | --- | --- |
| `workbenchFragment` | `feature.workbench.WorkbenchFragment` | 无，startDestination |
| `deepSeekFragment` | 现有 `DeepSeekFragment` | 无 |
| `apiKeyFunFragment` | 现有 `ApiKeyFunFragment` | `profileId: String?` |
| `settingsFragment` | 现有 `SettingsFragment` | 无 |
| `taskListFragment` | `feature.tasks.TaskListFragment` | `filter: String = TODAY` |
| `taskEditFragment` | `feature.tasks.TaskEditFragment` | `taskId: Long = -1` |
| `focusFragment` | `feature.focus.FocusFragment` | `taskId: Long = -1` |
| `focusHistoryFragment` | `feature.focus.FocusHistoryFragment` | 无 |
| `reportsFragment` | `feature.reports.ReportsFragment` | `rangeDays: Int = 7` |
| `resourceInboxFragment` | `feature.inbox.ResourceInboxFragment` | `source: String = HACKER_NEWS` |

详情 destination 进入时隐藏 `bottom_nav`；四个根 destination 显示。`MainActivity` 只负责顶层导航、Intent 分发和底栏可见性，不承载业务数据。

布局和适配器命名固定如下：

```text
res/layout/
├─ fragment_workbench.xml
├─ item_workbench_header.xml
├─ item_workbench_quick_add.xml
├─ item_workbench_section_header.xml
├─ item_workbench_task.xml
├─ item_workbench_focus.xml
├─ item_workbench_ai_resources.xml
├─ item_workbench_modules.xml
├─ item_workbench_review.xml
├─ fragment_task_list.xml
├─ item_task.xml
├─ fragment_task_edit.xml
├─ fragment_focus.xml
├─ fragment_focus_history.xml
├─ item_focus_session.xml
├─ fragment_reports.xml
├─ fragment_resource_inbox.xml
└─ item_resource.xml
```

`WorkbenchAdapter` 接收扁平的 `List<WorkbenchItem>`，ViewType 顺序为 `Header`、`QuickAdd`、`SectionHeader(Today)`、0 至 3 个 `TodayTask`、`SeeAllTasks`、`ActiveFocus`、`AiResources`、`Modules`、`DailyReview`。四个模块在 `item_workbench_modules.xml` 中使用静态 2 列 `GridLayout`，首页不嵌套可滚动 RecyclerView。

所有列表 Adapter 开启 stable ID。`TaskListAdapter`、`FocusHistoryAdapter` 和 `ResourceAdapter` 使用 `ListAdapter + DiffUtil.ItemCallback`；绑定时不得启动未取消的协程或 Animator。

## 6. 多 Key、同步与缓存

### 6.1 APIKEY.FUN Key 配置

阶段 3 将单 Key 迁移为 `ApiKeyFunProfile` JSON 列表：

```text
id UUID
alias String(1..30)
credentialRef String
fingerprint String SHA-256
isPrimaryForBalance Boolean
enabled Boolean
createdAt Long
```

Profile 列表只保存元数据；`credentialRef` 指向独立的 DataStore secret 项 `api_secret_apikeyfun_{id}`。阶段 3 仍沿用当前明文 DataStore 风险边界，阶段 5 由 `SecureKeyStore` 原位加密这些 secret 项，Profile 结构无需再次迁移。

迁移规则：

1. 读取旧 `apikey_fun_api_key`。
2. 非空且列表为空时创建别名“默认 Key”的 Profile，并设为余额主 Key。
3. 成功写入 Profile 元数据和对应 secret 项并回读验证后，删除旧 `apikey_fun_api_key` 字段。
4. 同一指纹不能重复加入。
5. 删除主 Key 前必须先指定另一个主 Key；最后一个 Key 可直接删除并清空余额缓存。

### 6.2 AI 同步

`UsageSyncWorker` 与现有 `WidgetUpdateWorker` 分工：

- `WidgetUpdateWorker`：保持轻量，只刷新两个余额和小组件。
- `UsageSyncWorker`：每天一次，并在用户打开报告页或手动刷新时按需执行；同步最近 30 天。
- APIKEY.FUN 多 Key 最大并发数为 3，单 Key 超时不取消其他 Key。
- 每次成功响应以 `(provider, credentialId, date, model)` upsert。
- DeepSeek 每次余额成功刷新后，将余额扣减估算归一化写入 `model="unknown"`、`isEstimated=true`。
- 只删除同一 credential、同一请求时间范围内服务端确认不存在的旧 APIKEY.FUN 明细，不能因部分失败清空缓存。
- 费用按币种分组；预算键也包含币种，不允许跨币种合计。
- 预算通知去重键为 `budget_alert_{provider}_{currency}_{period}_{threshold}_{periodStart}`，同一周期同一阈值只通知一次。

### 6.3 数据保留

- 任务、项目、复盘：用户主动删除前永久保留。
- 专注会话：默认保留 365 天，设置页可手动清理，不自动后台删除。
- AI 日用量：保留 365 天。
- DeepSeek 原始余额快照：继续保留 90 天。
- 未收藏信息流缓存：6 小时；收藏条目永久保留。

## 7. 提醒、权限与深链

- Android 13+ 只在用户首次保存提醒或开始带结束通知的专注时请求 `POST_NOTIFICATIONS`，不在冷启动请求。
- 权限拒绝后功能仍可运行，页面显示“通知未开启”，并提供系统设置入口。
- 通知渠道：`productivity_reminders` 和 `focus_sessions`。
- 唯一 Work 名称：`task_reminder_{taskId}`、`focus_completion_{sessionId}`。
- 修改提醒时间必须 replace 原 Work；完成、取消、删除任务必须 cancel。
- 通知 Intent 携带 `open_destination` 和实体 ID，`MainActivity.onNewIntent()` 也要处理。
- 不使用前台 Service；验证无引用后删除 Manifest 中 `FOREGROUND_SERVICE` 权限。

## 8. 安全与备份

阶段 5 实现 `SecureKeyStore`：

- Android Keystore alias：`deepseek_widget_api_key_v1`。
- 算法：`AES/GCM/NoPadding`，每条密文独立随机 12-byte IV。
- DataStore 只保存 `version + iv + ciphertext` 的 Base64 结构。
- 首次读取旧明文 Key 时加密迁移；验证可解密后才删除旧值。
- 解密失败不得覆盖密文，提示用户重新输入并允许删除损坏项。
- Release 构建不得记录 Key、Authorization header 或完整供应商响应。
- 将 `usesCleartextTraffic` 改为 `false`。
- 新增 `res/xml/backup_rules.xml` 和 `data_extraction_rules.xml`，排除 API Key DataStore 文件和安全材料；普通 Room 生产力数据允许备份。

数据导出使用 Storage Access Framework，JSON 顶层包含 `schemaVersion`、`exportedAt`、projects、tasks、focusSessions、dailyReviews、savedResources；`savedResources` 只导出 `isSaved=true` 的条目。导出文件不包含任何 API Key、Token、余额缓存或原始网络响应。

## 9. 分阶段编码清单

任何 agent 一次只执行一个阶段。每个阶段完成后必须先通过该阶段验收，再进入下一阶段。

### 阶段 0：基线冻结

状态：**Completed（2026-08-04）**。

目标：证明工作台开发前的 1.4.0 可复现。

操作：

1. 在正式目录运行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
2. 记录测试数量、APK 路径和 SHA-256 到 `docs/HANDOFF.md`。
3. 用 `aapt dump badging` 和 `aapt dump xmltree` 复查 AppWidget receiver、provider info 和初始布局。
4. 不修改业务代码；失败时先修复环境，不进入阶段 1。

完成条件：现有 17 个测试通过、lint 通过、debug APK 构建成功、小组件声明仍存在。

完成证据：17/17 单元测试通过，`:app:lintDebug` 和 `:app:assembleDebug` 成功；APK SHA-256 为 `4A98C92BC191DF3D1DB87AEE04C52F82A6D3FFFBCC55D7548CBABE6D72E565B8`。`aapt` 确认包名 `com.deepseek.widget`、版本 `1.4.0 (7)`、`provides-component:'app-widget'`、`DeepSeekWidgetProvider`、`ACTION_REFRESH`、`android.appwidget.provider`，且 `initialLayout` 资源 `0x7f0b0072` 映射为 `layout/widget_balance`。构建会打印 JDK 24 native-access 和 SDK XML 版本 3/4 的非阻断警告；阶段 1 保持现有工具链，不把警告与业务改动混合处理。下一位 agent 直接从阶段 1 开始。

### 阶段 1：导航与本地数据基础

版本：内部 `1.5.0-alpha01`，不对外发布。

修改：

- 根 `build.gradle.kts`：加入 KSP 和 Safe Args 插件版本。
- `app/build.gradle.kts`：加入 Navigation、ViewModel、Room 和测试依赖；配置 Room schema 输出目录。
- `activity_main.xml`：把 FrameLayout 替换为 `FragmentContainerView/NavHostFragment`。
- 新增 `res/navigation/main_nav_graph.xml`，先保持原 4 个根目的地。
- 修改 `MainActivity.kt`：使用 NavController + NavigationUI，处理底栏显示和 Intent 深链。
- 新增 `DeepSeekWidgetApp.kt`、`AppContainer.kt`、Room v1 四张表、DAO、Repository 和 ViewModelFactory。
- 新增数据库与 Repository 测试。

验收：

- 四个现有根页面行为和数据不变。
- 切换 Tab 后返回时保留滚动位置和已加载内容。
- Room schema JSON 已生成并纳入项目。
- 进程重建后导航不重复叠加 Fragment。
- 现有小组件刷新、点击和构建声明不变。

### 阶段 2：生产力 MVP

版本：`1.5.0`，versionCode `8`。

新增/修改：

- 新建 `feature/workbench`、`feature/tasks`、`feature/focus` 页面、ViewModel、适配器和布局。
- 新建 `TaskReminderWorker`、`FocusCompletionWorker` 和通知渠道初始化。
- `main_nav_graph.xml` 增加任务列表、任务编辑、专注、专注历史目的地。
- 底栏重排并让工作台成为默认目的地。
- 将应用显示名改为“AI 工作台”，替换 adaptive/monochrome 图标。
- `WorkbenchRegistry` 改为四个真实目的地；删除 `enabled=false` 和“敬请期待”路径。
- 实现工作台首页、任务 CRUD、今日计划、任务提醒、专注状态机和每日复盘。

验收：

- 快速新增、编辑、完成、恢复、删除、筛选和今日排序均可用。
- 任务提醒修改/完成/删除后不会出现旧通知。
- 专注在旋转、切后台和进程重建后显示正确剩余时间。
- 通知权限拒绝时不崩溃，应用内计时仍完整。
- 工作台不再存在无行为的可点击卡片。
- DeepSeek、APIKEY.FUN 和桌面小组件无回归。

### 阶段 3：AI 效率洞察与多 Key

版本：`1.6.0`，versionCode `9`。

新增/修改：

- Room v1 -> v2 migration 和迁移测试。
- `UsageInsightsRepository`、`UsageSyncWorker`、报告 ViewModel 和布局。
- APIKEY.FUN 设置改为多 Key Profile，完成旧单 Key 迁移。
- 供应商页加入“全部 / Key 别名”范围选择；主余额 Key 单独标记。
- 工作台 AI 摘要读取统一 Repository，不直接发网络请求。
- 现有 `UsageTrendView`、模型排行和数字增加可关闭的入场动画。
- 增加预算设置、80%/100% 去重提醒和周期对比。

验收：

- 至少用三个 fixture 覆盖：单 Key 多模型、多 Key 聚合、一个 Key 失败的部分成功。
- 相同 Key 不能重复添加；多个 Key 用量相加但余额不相加。
- DeepSeek 报告没有虚构请求数、Token 或模型。
- 同一时间范围与上一等长时间范围计算正确，跨月和本地时区测试通过。
- 离线可查看最后缓存及其时间，手动刷新错误不清空旧数据。
- 动画关闭时图表和数字立即显示最终值。

### 阶段 4：信息收件箱

版本：`1.7.0`，versionCode `10`。

新增/修改：

- Room v2 -> v3 migration 和迁移测试。
- Hacker News、GitHub remote model/client/parser。
- `ResourceInboxRepository`、列表页、收藏页和“转为任务”。
- 设置页加入可选 GitHub Token。
- Workbench 信息收件箱摘要显示收藏数量和最近更新时间。

验收：

- 两个数据源均有脱敏 fixture 和解析测试。
- HN 只并发 6 个 item 请求，30 条完成后可取消剩余工作。
- GitHub 限流、无网络、空数据和字段缺失均有稳定状态。
- 同一外部条目重复收藏不会生成重复行。
- 转为任务后再次操作定位到既有任务，不重复创建。
- 外链协议校验通过，不加载任意 scheme。

### 阶段 5：安全、数据工具和发布加固

版本：`1.8.0`，versionCode `11`。

新增/修改：

- `SecureKeyStore` 和所有旧 Key 的事务式迁移。
- 备份排除规则、关闭 cleartext、删除无用前台服务权限。
- 生产力数据 JSON 导出/导入和 schemaVersion 校验。
- R8 keep 规则覆盖 kotlinx.serialization、Room、Widget provider 和 Worker。
- release 构建、签名流程说明和真实设备测试。

验收：

- 升级安装后旧 Key 仍可查询，DataStore 不再出现明文 Key。
- 导出文件无密钥和 Authorization 内容；导入重复执行结果幂等。
- release minify 构建通过，主要页面、Worker 和小组件不因 R8 失效。
- Android 8、13、15/16 至少各验证一个系统版本或模拟器。
- 至少验证 Pixel Launcher 与一个 OEM Launcher 的小组件添加、缩放、刷新和点击。

## 10. 测试矩阵

### 10.1 JVM 单元测试

- 任务状态转换、日期筛选、排序和完成率。
- 专注状态机、暂停累计、超时恢复和取消。
- DeepSeek 扣减估算原有测试全部保留。
- APIKEY.FUN 单 Key、多模型、多 Key、部分失败、币种和时区。
- AI 周期对比、未知值、预算阈值去重。
- HN/GitHub JSON 缺字段和未知字段容错。
- SecureKeyStore 编解密封装中的纯 Kotlin 格式校验。

### 10.2 Android instrumentation 测试

- Room v1 DAO CRUD 和 Flow。
- v1 -> v2 -> v3 migration，迁移前后行数和字段值一致。
- WorkManager 唯一任务 replace/cancel 行为。
- 导航根页面、详情返回和 Intent 深链。
- 任务创建、完成、提醒权限拒绝。
- 专注启动、暂停、恢复和进程重建。

### 10.3 每阶段固定命令

工作目录均为 `D:\CodexProjects\DeepSeekWidget`：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

无设备时第三条允许标记为“未执行”，但第二条必须保证 `androidTest` 编译通过，并在交接中列出真实设备待办。

发布阶段补充：

```powershell
.\gradlew.bat :app:assembleRelease
& "$env:ANDROID_HOME\build-tools\36.0.0\aapt.exe" dump badging app\build\outputs\apk\release\app-release.apk
```

若本机 Build Tools 版本不同，应先从 SDK 目录定位实际 `aapt.exe`，不得硬删或复制 SDK。

## 11. 全局完成定义

一个阶段只有同时满足以下条件才能称为完成：

- 实现了该阶段全部入口、空态、加载态、成功态、错误态和权限拒绝态。
- 所有看起来可点击的控件都有实际行为；无“敬请期待”伪入口。
- 新增字符串集中在 `strings.xml`，没有硬编码 Key、Token 或中文 Toast。
- 数据变更有迁移或明确证明无需迁移；没有 destructive migration。
- `testDebugUnitTest`、`lintDebug`、`assembleDebug` 通过。
- AppWidget 的 receiver、provider XML、初始布局和刷新 action 经 `aapt` 复查。
- 未修改 `D:\CodexProjects\DeepSeekWidget-work-archive`。
- `docs/HANDOFF.md` 已原位刷新，记录实际完成内容、验证证据、遗留风险和下一阶段首个动作。

## 12. 后续 agent 执行规则

1. 只以 `D:\CodexProjects\DeepSeekWidget` 为源码，归档目录只读。
2. 从本文档最早未完成阶段开始，不跨阶段同时重写。
3. 阶段内按“数据层 -> Repository/ViewModel -> 页面 -> Worker/通知 -> 测试”顺序提交改动。
4. 不因新增工作台而改写已经验证的供应商解析器；需要改时先增加真实响应 fixture。
5. 不把自定义图表 View、RecyclerView、MaterialCardView 或复杂布局放进 RemoteViews。
6. 不顺手升级 Kotlin、AGP、Gradle、Room 或 Navigation；工具链升级单独验证。
7. 发现外部 API 契约变化时，以官方文档或实际脱敏响应为证据更新本方案和 HANDOFF，再编码适配。
8. 每完成一个阶段立即刷新唯一 `docs/HANDOFF.md`，不创建日期版交接副本。

## 13. 外部技术依据

- Android Room 用于非简单结构化本地数据、编译期 SQL 校验和迁移：[Android Developers - Room](https://developer.android.com/training/data-storage/room/)
- Fragment 页面由 Navigation graph 和 NavController 管理：[Android Developers - Navigation graph](https://developer.android.com/guide/navigation/design)
- Hacker News 官方 Firebase API 的 item、topstories 和版本契约：[HackerNews/API](https://github.com/HackerNews/API)
- GitHub 仓库搜索契约：[GitHub REST API - Search repositories](https://docs.github.com/en/rest/search/search#search-repositories)

本文档已经消除本路线内的产品和技术开放项。只有用户改变产品目标、供应商 API 无法按当前契约返回数据、或平台权限规则发生变化时，后续 agent 才需要暂停编码并重新确认。
