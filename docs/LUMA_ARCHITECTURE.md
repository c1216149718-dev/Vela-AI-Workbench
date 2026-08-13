# LUMA 架构设计文档

> **LUMA** — Lightweight Usage Monitoring Architecture
> 为 DeepSeekWidget 设计的全新 Android 应用框架
> 版本：1.0 · 日期：2026-08-03

---

## 目录

1. [现有框架全面梳理](#1-现有框架全面梳理)
2. [新框架总体架构](#2-新框架总体架构)
3. [模块划分与职责边界](#3-模块划分与职责边界)
4. [数据流向与协作关系](#4-数据流向与协作关系)
5. [新旧功能映射表](#5-新旧功能映射表)
6. [设计决策与改进点](#6-设计决策与改进点)
7. [可扩展性设计](#7-可扩展性设计)
8. [迁移路径](#8-迁移路径)

---

## 1. 现有框架全面梳理

### 1.1 功能清单（21 个 Kotlin 源文件，9 个布局，3 个测试）

#### 核心功能

| # | 功能 | 入口 | 涉及文件 |
|---|------|------|---------|
| F1 | DeepSeek 余额查询与展示 | DeepSeekFragment | DeepSeekApiClient, AppPreferences, WidgetUiHelper |
| F2 | APIKEY.FUN 余额查询与展示 | ApiKeyFunFragment | DeepSeekApiClient, AppPreferences, WidgetUiHelper |
| F3 | DeepSeek 消耗追踪（余额快照差值） | DeepSeekFragment | BalanceDeltaAggregator, BalanceSnapshot, AppPreferences |
| F4 | APIKEY.FUN 用量仪表盘（全体模型对比） | ApiKeyFunFragment | DeepSeekApiClient, UsageComparison, UsageDashboardView |
| F5 | 时间范围切换（1/3/5/7/14/28/30 天） | UsageDashboardView | UsageDashboardView, UsageComparison |
| F6 | 本期 vs 上期用量对比 | UsageDashboardView | UsageComparison, UsageTrendView |
| F7 | 模型排行（费用/请求/Token 度量切换） | UsageDashboardView | UsageDashboardView, ModelUsageStat |
| F8 | 桌面小组件余额展示 | DeepSeekWidgetProvider | WidgetUiHelper, AppPreferences, WidgetUpdateWorker |
| F9 | 后台定时刷新余额 | WidgetUpdateWorker | WorkManager, DeepSeekApiClient, AppPreferences |
| F10 | 工作台模块展示（6 个占位模块） | WorkbenchFragment | WorkbenchRegistry, WorkbenchAdapter, WorkbenchModule |
| F11 | 刷新间隔配置 | SettingsFragment | AppPreferences, WidgetUpdateWorker |
| F12 | 版本信息展示 | SettingsFragment | BuildConfig |

#### 辅助功能

| # | 功能 | 涉及文件 |
|---|------|---------|
| A1 | API Key 输入与测试连接 | DeepSeekFragment, ApiKeyFunFragment, DeepSeekApiClient |
| A2 | 余额缓存（DataStore 持久化） | AppPreferences |
| A3 | 余额快照存储与读取 | BalanceSnapshot, BalanceSnapshotLedger, AppPreferences |
| A4 | 本地手工记账（已废弃） | DeepSeekUsageEntry, DeepSeekUsageLedger, DeepSeekUsageAggregator |
| A5 | 图表动画（ValueAnimator） | UsageDashboardView, UsageTrendView |
| A6 | 主题色切换（蓝/琥珀） | UsageDashboardView.configure, UsageTrendView.setAccentColor |

#### 死代码 / 残留

| # | 项目 | 状态 |
|---|------|------|
| D1 | dialog_add_usage.xml | 孤儿布局，无代码引用，5 个字符串缺失 |
| D2 | ModelsResponse.kt | 模型类已定义但无调用 |
| D3 | FOREGROUND_SERVICE 权限 | Manifest 声明但代码未使用 |
| D4 | claude_widget_* 颜色 | 从另一个项目复用的遗留命名 |
| D5 | DeepSeekUsageEntry/Ledger/Aggregator | 手工记账已被余额快照替代 |

### 1.2 核心业务逻辑

```
DeepSeek 消耗追踪流程：
  用户点击"测试连接" → fetchBalance() → 解析余额数值
    → 保存 BalanceSnapshot(timestamp, balance)
    → 余额缓存写入 DataStore
    → 小组件刷新
    → 仪表盘：BalanceDeltaAggregator.dailyPoints()
      → 按天分组快照 → 相邻快照差值 = 当日消耗
      → UsageComparison.normalize() 补齐零值日期

APIKEY.FUN 用量展示流程：
  用户进入页面 → loadUsage()
    → 并行请求 current + comparison 两个时段
    → parseApiKeyFunUsageDetails() 容错解析 JSON
    → UsageComparison.normalize() 日期归一化
    → UsageDashboardView.showUsage() 渲染
      → 指标卡动画 → 趋势图 → 模型排行

桌面小组件刷新流程：
  WorkManager 周期触发 → WidgetUpdateWorker.doWork()
    → 读取两个账户 API Key
    → 并行 fetchBalance() × 2
    → 结果写入 DataStore 缓存
    → 广播 APPWIDGET_UPDATE → DeepSeekWidgetProvider.onUpdate()
    → WidgetUiHelper.buildRemoteViews() → 更新 RemoteViews
```

### 1.3 数据流向

```
                    ┌──────────────┐
                    │   DeepSeek    │
                    │   API (HTTP)  │
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │ DeepSeekApi  │
                    │   Client     │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────┴─────┐ ┌───┴────┐ ┌────┴─────┐
        │ DataStore │ │Fragment│ │  Widget  │
        │ (缓存)    │ │ (UI)   │ │ Provider │
        └───────────┘ └────────┘ └──────────┘

                    ┌──────────────┐
                    │ APIKEY.FUN   │
                    │   API (HTTP) │
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │ DeepSeekApi  │
                    │   Client     │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────┴─────┐ ┌───┴────┐ ┌────┴─────┐
        │ DataStore │ │Fragment│ │  Widget  │
        │ (缓存)    │ │ (UI)   │ │ Provider │
        └───────────┘ └────────┘ └──────────┘
```

### 1.4 对外接口

| 接口 | 类型 | 说明 |
|------|------|------|
| `GET api.deepseek.com/user/balance` | HTTP | DeepSeek 余额查询 |
| `GET api.apikey.fun/v1/usage` | HTTP | APIKEY.FUN 余额 + 用量（带 days/start_date/end_date 参数） |
| `APPWIDGET_UPDATE` | Broadcast | 系统小组件更新 |
| `com.deepseek.widget.ACTION_REFRESH` | Broadcast | 自定义刷新广播 |
| `MainActivity` | Intent | 点击小组件打开主界面 |
| `WidgetUpdateWorker.schedulePeriodic()` | WorkManager | 周期任务调度 |

### 1.5 现有框架问题诊断

| 问题 | 严重度 | 说明 |
|------|--------|------|
| 无分层架构 | **高** | Fragment 直接调 API Client 和 DataStore，UI/业务/数据混在一起 |
| 无依赖注入 | **高** | 每个 Fragment 手动 new 所有依赖，无法替换 Mock |
| 单 API Client 处理两个服务 | **中** | DeepSeekApiClient 同时处理 DeepSeek 和 APIKEY.FUN，职责不清 |
| 单 Preferences 类承载所有状态 | **中** | AppPreferences 是 God Object，API Key/余额/快照/账本全在一起 |
| 无 ViewModel | **中** | 配置更改（旋转）时数据丢失，无生命周期感知 |
| 死代码残留 | **低** | dialog_add_usage.xml、ModelsResponse、FOREGROUND_SERVICE |
| 尺寸硬编码 | **低** | 无 dimens.xml，布局中直接写 dp 值 |

---

## 2. 新框架总体架构

### 2.1 架构风格：Feature-Modular MVVM + Service Registry

LUMA 采用 **MVVM + Repository + UseCase** 三层架构，按功能模块（Feature Module）组织代码，工作台采用 **Service Registry** 插件化模式。

```
┌────────────────────────────────────────────────────────────────┐
│                      Presentation Layer                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ DeepSeek │  │ApiKeyFun │  │Workbench │  │ Settings │      │
│  │  Screen  │  │  Screen  │  │  Screen  │  │  Screen  │      │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘      │
│       │             │             │             │             │
│  ┌────┴─────┐  ┌───┴──────┐  ┌───┴──────┐  ┌───┴──────┐     │
│  │ DeepSeek │  │ApiKeyFun │  │Workbench │  │ Settings │      │
│  │ViewModel │  │ViewModel │  │ViewModel │  │ViewModel │      │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘      │
├───────┼─────────────┼─────────────┼─────────────┼─────────────┤
│       │             │    Domain Layer           │             │
│  ┌────┴─────────────┴─────────────┴─────────────┴─────┐      │
│  │                  Use Cases                          │      │
│  │  ┌──────────┐ ┌──────────┐ ┌───────────┐          │      │
│  │  │ FetchBal │ │FetchUsage│ │TrackSpend │          │      │
│  │  │ anceUC   │ │ anceUC   │ │ ingUC     │          │      │
│  │  └──────────┘ └──────────┘ └───────────┘          │      │
│  │  ┌──────────┐ ┌──────────┐ ┌───────────┐          │      │
│  │  │RefreshWid│ │ComparePe │ │ManageMod  │          │      │
│  │  │ getUC    │ │ riodsUC  │ │ ulesUC    │          │      │
│  │  └──────────┘ └──────────┘ └───────────┘          │      │
│  └────────────────────────┬───────────────────────────┘      │
│                           │                                   │
│  ┌────────────────────────┴───────────────────────────┐      │
│  │              Domain Models (Pure)                   │      │
│  │  Account · Balance · UsageReport · ModuleInfo      │      │
│  └────────────────────────────────────────────────────┘      │
├───────────────────────────────────────────────────────────────┤
│                        Data Layer                             │
│  ┌────────────────┐  ┌────────────────┐  ┌───────────────┐  │
│  │ AccountRepo    │  │ UsageRepo      │  │ SnapshotRepo  │  │
│  │ (接口+实现)    │  │ (接口+实现)    │  │ (接口+实现)   │  │
│  └───────┬────────┘  └───────┬────────┘  └───────┬───────┘  │
│          │                   │                    │          │
│  ┌───────┴────────┐  ┌───────┴────────┐  ┌───────┴───────┐  │
│  │ DeepSeekApi    │  │ ApiKeyFunApi   │  │ LocalStore    │  │
│  │ (HTTP Source)  │  │ (HTTP Source)  │  │ (DataStore)   │  │
│  └────────────────┘  └────────────────┘  └───────────────┘  │
├───────────────────────────────────────────────────────────────┤
│                     Framework Layer                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │  Widget  │  │ WorkMgr  │  │   DI     │  │  Theme   │    │
│  │ Provider │  │ Worker   │  │ Container│  │  Engine  │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
└───────────────────────────────────────────────────────────────┘
```

### 2.2 包结构

```
com.deepseek.widget/
├── core/
│   ├── di/
│   │   └── ServiceLocator.kt          # 手动 DI 容器（轻量，不引入 Hilt）
│   ├── result/
│   │   └── LumaResult.kt              # 统一结果封装（Success/Error/Loading）
│   └── theme/
│       └── LumaTheme.kt               # 主题配置（色彩/圆角/间距常量）
│
├── domain/
│   ├── model/
│   │   ├── Account.kt                 # 账户领域模型
│   │   ├── Balance.kt                 # 余额领域模型
│   │   ├── UsageReport.kt             # 用量报告领域模型
│   │   ├── SpendingTracker.kt         # 消耗追踪领域模型
│   │   └── ModuleInfo.kt              # 工作台模块领域模型
│   ├── repository/
│   │   ├── AccountRepository.kt       # 账户仓库接口
│   │   ├── UsageRepository.kt         # 用量仓库接口
│   │   └── SnapshotRepository.kt      # 快照仓库接口
│   └── usecase/
│       ├── FetchBalanceUseCase.kt     # 获取余额
│       ├── FetchUsageUseCase.kt       # 获取用量
│       ├── TrackSpendingUseCase.kt    # 追踪消耗
│       ├── ComparePeriodsUseCase.kt   # 对比时段
│       ├── RefreshWidgetUseCase.kt    # 刷新小组件
│       └── ManageModulesUseCase.kt    # 管理模块
│
├── data/
│   ├── local/
│   │   ├── LumaDataStore.kt           # DataStore 统一入口
│   │   ├── AccountStore.kt            # 账户数据存储
│   │   ├── SnapshotStore.kt           # 快照数据存储
│   │   └── PreferenceKeys.kt          # 所有 DataStore Key 集中定义
│   ├── remote/
│   │   ├── DeepSeekApiService.kt      # DeepSeek HTTP API
│   │   ├── ApiKeyFunApiService.kt     # APIKEY.FUN HTTP API
│   │   ├── dto/
│   │   │   ├── DeepSeekBalanceDto.kt  # DeepSeek 响应 DTO
│   │   │   └── ApiKeyFunUsageDto.kt   # APIKEY.FUN 响应 DTO
│   │   └── parser/
│   │       ├── BalanceParser.kt       # 余额解析器
│   │       └── UsageParser.kt         # 用量解析器
│   └── repository/
│       ├── AccountRepositoryImpl.kt   # 账户仓库实现
│       ├── UsageRepositoryImpl.kt     # 用量仓库实现
│       └── SnapshotRepositoryImpl.kt  # 快照仓库实现
│
├── presentation/
│   ├── common/
│   │   ├── LumaActivity.kt            # 主 Activity（导航宿主）
│   │   ├── LumaViewModel.kt           # ViewModel 基类
│   │   ├── ui/
│   │   │   ├── LumaCard.kt            # 通用卡片组件
│   │   │   ├── LumaBadge.kt           # 通用徽标组件
│   │   │   ├── LumaToggleGroup.kt     # 通用切换按钮组
│   │   │   ├── LumaChartView.kt       # 通用图表视图
│   │   │   └── LumaDashboardView.kt   # 通用仪表盘视图
│   │   └── theme/
│   │       └── LumaColors.kt          # 主题色定义
│   ├── deepseek/
│   │   ├── DeepSeekFragment.kt        # DeepSeek 页面
│   │   └── DeepSeekViewModel.kt       # DeepSeek 页面 ViewModel
│   ├── apikeyfun/
│   │   ├── ApiKeyFunFragment.kt       # APIKEY.FUN 页面
│   │   └── ApiKeyFunViewModel.kt      # APIKEY.FUN 页面 ViewModel
│   ├── workbench/
│   │   ├── WorkbenchFragment.kt       # 工作台页面
│   │   ├── WorkbenchViewModel.kt      # 工作台 ViewModel
│   │   ├── WorkbenchModuleRegistry.kt # 模块注册表
│   │   └── WorkbenchModuleAdapter.kt  # 模块网格适配器
│   └── settings/
│       ├── SettingsFragment.kt        # 设置页面
│       └── SettingsViewModel.kt       # 设置 ViewModel
│
├── widget/
│   ├── LumaWidgetProvider.kt          # 小组件 Provider
│   ├── LumaWidgetRenderer.kt          # RemoteViews 构建器
│   └── LumaWidgetUpdater.kt           # 小组件更新触发器
│
└── worker/
    ├── BalanceRefreshWorker.kt        # 余额刷新 Worker
    └── WorkerScheduler.kt             # Worker 调度管理
```

---

## 3. 模块划分与职责边界

### 3.1 分层职责

| 层 | 职责 | 不知道 | 知道 |
|----|------|--------|------|
| **Presentation** | 渲染 UI、处理用户交互、观察 ViewModel 状态 | 数据来源、网络细节 | ViewModel、Domain Model |
| **Domain** | 业务逻辑、数据转换、规则校验 | Android 框架、HTTP、数据库 | Domain Model、Repository 接口 |
| **Data** | 数据存取、网络请求、缓存管理 | UI、业务规则 | Repository 接口、DTO、DataStore |
| **Framework** | 平台集成、小组件、后台任务、DI | 业务逻辑 | 所有层（通过 ServiceLocator） |

### 3.2 模块间依赖规则

```
允许方向：Presentation → Domain → Data
禁止方向：Data → Domain、Data → Presentation、Domain → Presentation

Domain 层不依赖任何 Android 框架类（除 kotlinx.coroutines）
Data 层不依赖 Presentation 层的任何类
Presentation 层不直接访问 Data 层（必须通过 Domain 层）
```

### 3.3 核心模块详解

#### ServiceLocator（手动 DI）

```kotlin
object ServiceLocator {
    // 按需提供单例，支持测试时替换
    val accountRepository: AccountRepository by lazy { AccountRepositoryImpl(...) }
    val usageRepository: UsageRepository by lazy { UsageRepositoryImpl(...) }
    val snapshotRepository: SnapshotRepository by lazy { SnapshotRepositoryImpl(...) }

    // 测试时注入 Mock
    fun overrideForTesting(
        accountRepo: AccountRepository? = null,
        usageRepo: UsageRepository? = null
    ) { /* ... */ }
}
```

**为什么不用 Hilt**：项目规模小（~25 个类），Hilt 引入的 APT 处理增加构建时间，手动 ServiceLocator 更轻量且足够。

#### LumaResult（统一结果封装）

```kotlin
sealed class LumaResult<out T> {
    data class Success<T>(val data: T) : LumaResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : LumaResult<Nothing>()
    data object Loading : LumaResult<Nothing>()
}
```

替代现有的 `Result<T>` + `try-catch` 混合模式，统一错误处理。

#### 领域模型（Pure Kotlin，无 Android 依赖）

```kotlin
// domain/model/Account.kt
data class Account(
    val provider: AccountProvider,
    val apiKey: String,
    val balance: Balance?,
    val isConfigured: Boolean
)

// domain/model/Balance.kt
data class Balance(
    val total: Double,
    val granted: Double,
    val toppedUp: Double,
    val currency: String,
    val lastUpdated: Long
)

// domain/model/UsageReport.kt
data class UsageReport(
    val period: DateRange,
    val dailyPoints: List<DailyUsage>,
    val modelBreakdown: List<ModelUsage>,
    val comparisonPeriod: DateRange?,
    val comparisonDailyPoints: List<DailyUsage>?
)
```

---

## 4. 数据流向与协作关系

### 4.1 DeepSeek 余额 + 消耗追踪（完整链路）

```
用户打开 DeepSeek 页
  │
  ▼
DeepSeekFragment.onViewCreated()
  │
  ▼
DeepSeekViewModel.loadInitialData()
  │
  ├──→ FetchBalanceUseCase.execute(Provider.DEEPSEEK)
  │      │
  │      ▼
  │    AccountRepository.getBalance(Provider.DEEPSEEK)
  │      │
  │      ├──→ 缓存有数据？ → 返回缓存（先显示）
  │      │
  │      └──→ 无缓存 → DeepSeekApiService.fetchBalance()
  │                │
  │                ▼
  │             BalanceParser.parse(json)
  │                │
  │                ▼
  │             AccountStore.save(balance) ← 写入缓存
  │             SnapshotStore.add(balance) ← 写入快照
  │                │
  │                ▼
  │             返回 LumaResult.Success(Balance)
  │
  ├──→ TrackSpendingUseCase.execute(Provider.DEEPSEEK, days)
  │      │
  │      ▼
  │    SnapshotRepository.getSnapshots(days)
  │      │
  │      ▼
  │    SnapshotStore.read() ← 读取快照序列
  │      │
  │      ▼
  │    BalanceDeltaAggregator.dailyPoints() ← 差值计算
  │      │
  │      ▼
  │    ComparePeriodsUseCase.normalize() ← 日期归一化
  │      │
  │      ▼
  │    返回 LumaResult.Success(UsageReport)
  │
  ▼
DeepSeekFragment 观察 ViewModel.uiState
  │
  ▼
渲染 LumaDashboardView
```

### 4.2 APIKEY.FUN 用量展示（完整链路）

```
用户打开 APIKEY.FUN 页
  │
  ▼
ApiKeyFunViewModel.loadUsage(days)
  │
  ▼
FetchUsageUseCase.execute(Provider.APIKEY_FUN, days)
  │
  ├──→ UsageRepository.getUsage(days)
  │      │
  │      ▼
  │    ApiKeyFunApiService.fetchUsage(days, startDate, endDate)
  │      │
  │      ▼
  │    UsageParser.parse(json) ← 容错解析
  │      │
  │      ▼
  │    返回 LumaResult.Success(UsageReport)
  │
  ├──→ UsageRepository.getUsage(days * 2) ← 对比期
  │      │
  │      ▼
  │    返回 LumaResult.Success(ComparisonReport)
  │
  ▼
ApiKeyFunFragment 观察 ViewModel.uiState
  │
  ▼
渲染 LumaDashboardView（含对比数据）
```

### 4.3 小组件刷新（完整链路）

```
WorkManager 周期触发
  │
  ▼
BalanceRefreshWorker.doWork()
  │
  ▼
RefreshWidgetUseCase.execute()
  │
  ├──→ FetchBalanceUseCase.execute(Provider.DEEPSEEK)
  ├──→ FetchBalanceUseCase.execute(Provider.APIKEY_FUN)
  │
  ▼
LumaWidgetUpdater.requestUpdate()
  │
  ▼
发送 APPWIDGET_UPDATE 广播
  │
  ▼
LumaWidgetProvider.onUpdate()
  │
  ▼
LumaWidgetRenderer.buildRemoteViews()
  │
  ├──→ AccountStore.read(Provider.DEEPSEEK)
  ├──→ AccountStore.read(Provider.APIKEY_FUN)
  │
  ▼
更新 RemoteViews
```

---

## 5. 新旧功能映射表

### 5.1 核心功能映射

| 原功能 | 原实现位置 | 新框架实现位置 | 实现方式 |
|--------|-----------|---------------|---------|
| F1 DeepSeek 余额查询 | DeepSeekFragment → DeepSeekApiClient | FetchBalanceUseCase → AccountRepository → DeepSeekApiService | UseCase 编排，Repository 缓存 |
| F2 APIKEY.FUN 余额查询 | ApiKeyFunFragment → DeepSeekApiClient | FetchBalanceUseCase → AccountRepository → ApiKeyFunApiService | 同上，拆分为独立 API Service |
| F3 DeepSeek 消耗追踪 | DeepSeekFragment → BalanceDeltaAggregator | TrackSpendingUseCase → SnapshotRepository → BalanceDeltaAggregator | UseCase 封装聚合逻辑 |
| F4 APIKEY.FUN 用量仪表盘 | ApiKeyFunFragment → DeepSeekApiClient | FetchUsageUseCase → UsageRepository → ApiKeyFunApiService + UsageParser | 独立解析器，更清晰 |
| F5 时间范围切换 | UsageDashboardView | LumaDashboardView + ComparePeriodsUseCase | 逻辑移至 UseCase，View 只负责渲染 |
| F6 本期 vs 上期对比 | UsageComparison | ComparePeriodsUseCase | 纯函数，可单测 |
| F7 模型排行切换 | UsageDashboardView | LumaDashboardView | 保留，样式升级 |
| F8 桌面小组件 | DeepSeekWidgetProvider | LumaWidgetProvider + LumaWidgetRenderer | 渲染逻辑独立 |
| F9 后台定时刷新 | WidgetUpdateWorker | BalanceRefreshWorker + RefreshWidgetUseCase | Worker 只做调度，逻辑在 UseCase |
| F10 工作台模块 | WorkbenchFragment | WorkbenchFragment + WorkbenchModuleRegistry | 保留注册表模式，增加模块生命周期 |
| F11 刷新间隔配置 | SettingsFragment | SettingsFragment + WorkerScheduler | 调度逻辑独立 |
| F12 版本信息 | SettingsFragment | SettingsFragment | 保留 |

### 5.2 辅助功能映射

| 原功能 | 原实现位置 | 新框架实现位置 | 变化 |
|--------|-----------|---------------|------|
| A1 API Key 测试连接 | 两个 Fragment 各自实现 | FetchBalanceUseCase | 统一入口，消除重复 |
| A2 余额缓存 | AppPreferences | AccountStore + AccountRepository | 职责分离 |
| A3 快照存储 | AppPreferences + BalanceSnapshotLedger | SnapshotStore + SnapshotRepository | 同上 |
| A4 手工记账 | DeepSeekUsageEntry/Ledger | **移除** | 已被快照追踪替代 |
| A5 图表动画 | UsageDashboardView/UsageTrendView | LumaDashboardView/LumaChartView | 保留，样式升级 |
| A6 主题色切换 | UsageDashboardView.configure | LumaTheme + LumaColors | 全局主题引擎 |

### 5.3 死代码处理

| 项目 | 处理方式 |
|------|---------|
| D1 dialog_add_usage.xml | **删除** |
| D2 ModelsResponse.kt | **删除**（或移至 data/remote/dto/ 备用） |
| D3 FOREGROUND_SERVICE 权限 | **从 Manifest 移除** |
| D4 claude_widget_* 颜色 | **重命名为 widget_*** |
| D5 DeepSeekUsageEntry/Ledger/Aggregator | **删除** |

---

## 6. 设计决策与改进点

### 6.1 为什么选 MVVM 而不是 MVI 或 MVP？

| 架构 | 优点 | 缺点 | 适合场景 |
|------|------|------|---------|
| MVP | 简单直接 | Presenter 与 View 耦合、接口膨胀 | 小型项目 |
| MVI | 单向数据流、可预测 | 样板代码多、学习曲线陡 | 复杂状态管理 |
| **MVVM** | **Google 官方推荐、生命周期感知、样板少** | 双向绑定可能隐藏逻辑 | **中小型 Android 项目** |

**选择 MVVM 的理由**：
- 项目规模适中（~25 个类），MVI 的 Reducer/Intent/State 三层封装过于重型
- ViewModel 天然解决配置更改（旋转）时的数据恢复问题
- LiveData/StateFlow 与 DataStore Flow 无缝衔接
- Google 官方架构指南推荐

### 6.2 为什么用手动 ServiceLocator 而不是 Hilt？

- 项目只有 ~10 个需要注入的依赖，Hilt 的 APT 处理增加 3-5 秒构建时间
- 手动 ServiceLocator 约 50 行代码，完全可控
- 测试时可轻松替换 Mock 实现
- 不引入额外注解处理器

### 6.3 为什么拆分 DeepSeekApiClient？

原 `DeepSeekApiClient` 同时处理两个完全不同的 API 服务，违反单一职责原则。拆分为：
- `DeepSeekApiService` — 只处理 api.deepseek.com
- `ApiKeyFunApiService` — 只处理 api.apikey.fun

每个 Service 独立管理自己的 URL、Header、解析逻辑。共享的 OkHttpClient 实例通过构造注入。

### 6.4 为什么引入 UseCase 层？

原架构中 Fragment 直接编排"先取缓存 → 再请求网络 → 再聚合 → 再渲染"的逻辑，导致：
- 业务逻辑散落在 UI 层
- 无法单元测试
- 多个 Fragment 重复实现相同逻辑

UseCase 将每个业务操作封装为独立的可测试单元：
```kotlin
class FetchBalanceUseCase(
    private val accountRepo: AccountRepository,
    private val snapshotRepo: SnapshotRepository
) {
    suspend fun execute(provider: AccountProvider): LumaResult<Balance> {
        // 1. 尝试读缓存
        // 2. 请求网络
        // 3. 更新缓存 + 快照
        // 4. 返回结果
    }
}
```

### 6.5 为什么引入 LumaTheme？

原架构中颜色散落在 colors.xml 和各个布局中，WorkbenchFragment 的模块色与 DashboardView 的主题色没有关联。LumaTheme 提供：
- 全局色彩常量（主色/辅色/语义色）
- 圆角/间距/字号常量（替代硬编码 dp/sp）
- 深色/浅色主题切换支持（预留）

### 6.6 关键改进点总结

| 改进 | 原框架 | 新框架 | 收益 |
|------|--------|--------|------|
| 架构分层 | 无分层，Fragment 直连 API | MVVM + Repository + UseCase 三层 | 可测试、可维护 |
| 依赖管理 | 手动 new，无法替换 | ServiceLocator 单例 + 可覆盖 | 可 Mock、可测试 |
| 错误处理 | try-catch + Result 混用 | LumaResult 统一封装 | 一致的错误体验 |
| 数据模型 | DTO 与 UI 模型混用 | Domain Model（Pure）与 DTO 分离 | 清晰的边界 |
| API 拆分 | 单 Client 处理两个服务 | 独立 ApiService 每个服务 | 单一职责 |
| 死代码 | 5 处残留 | 全部清除 | 减少噪音 |
| 尺寸管理 | 硬编码 dp/sp | LumaTheme 常量 | 一致性 |
| 主题系统 | 无 | LumaTheme + LumaColors | 可切换主题 |

---

## 7. 可扩展性设计

### 7.1 新增 AI 服务提供商

```kotlin
// 1. 定义新的 API Service
class NewProviderApiService(private val client: OkHttpClient) { /* ... */ }

// 2. 在 AccountProvider 枚举中注册
enum class AccountProvider(val prefix: String, val displayName: String) {
    DEEPSEEK("deepseek", "DeepSeek"),
    APIKEY_FUN("apikey_fun", "APIKEY.FUN"),
    NEW_PROVIDER("new_provider", "New Provider")  // 新增
}

// 3. 实现 Repository
class NewProviderRepositoryImpl : AccountRepository { /* ... */ }

// 4. 在 ServiceLocator 注册
val newProviderRepository: AccountRepository by lazy { NewProviderRepositoryImpl(...) }

// 5. 创建 Fragment + ViewModel（复用 LumaDashboardView）
```

**无需修改的领域层**：UseCase 通过 AccountProvider 枚举自动适配新提供商。

### 7.2 新增工作台模块

```kotlin
// 1. 在 WorkbenchModuleRegistry 注册
object WorkbenchModuleRegistry {
    val modules = listOf(
        WorkbenchModule("hot_news", ...),
        WorkbenchModule("github_trending", ...),
        WorkbenchModule("new_module", ..., enabled = true)  // 新增
    )
}

// 2. 实现模块页面（可选：独立 Fragment 或内嵌 View）
class NewModuleFragment : Fragment() { /* ... */ }
```

### 7.3 新增图表类型

```kotlin
// LumaChartView 支持插拔式渲染器
interface ChartRenderer {
    fun render(canvas: Canvas, data: List<DailyUsage>, theme: LumaTheme)
}

class LineChartRenderer : ChartRenderer { /* ... */ }
class BarChartRenderer : ChartRenderer { /* ... */ }
class PieChartRenderer : ChartRenderer { /* ... */ }
```

### 7.4 深色主题支持

```kotlin
// LumaColors 预留深色主题
object LumaColors {
    val light = LumaColorScheme(...)
    val dark = LumaColorScheme(...)

    fun current(isDark: Boolean) = if (isDark) dark else light
}
```

---

## 8. 迁移路径

### 阶段 1：基础设施（不影响现有功能）

1. 创建 `core/di/ServiceLocator.kt`
2. 创建 `core/result/LumaResult.kt`
3. 创建 `core/theme/LumaTheme.kt`
4. 创建 `domain/model/` 下的领域模型
5. 创建 `domain/repository/` 下的仓库接口

### 阶段 2：数据层重构

6. 创建 `data/local/LumaDataStore.kt` + `AccountStore.kt` + `SnapshotStore.kt`
7. 创建 `data/remote/DeepSeekApiService.kt` + `ApiKeyFunApiService.kt`
8. 创建 `data/remote/parser/BalanceParser.kt` + `UsageParser.kt`
9. 创建 `data/repository/AccountRepositoryImpl.kt` + `UsageRepositoryImpl.kt` + `SnapshotRepositoryImpl.kt`

### 阶段 3：领域层

10. 创建所有 UseCase

### 阶段 4：Presentation 层

11. 创建 `presentation/common/ui/` 下的通用组件
12. 创建各页面 ViewModel
13. 重写各页面 Fragment（使用 ViewModel + 通用组件）
14. 重写 LumaActivity

### 阶段 5：Framework 层

15. 重写 Widget Provider + Renderer
16. 重写 Worker + Scheduler
17. 清理 Manifest（移除 FOREGROUND_SERVICE）

### 阶段 6：清理与验证

18. 删除死代码（dialog_add_usage.xml、ModelsResponse.kt、旧类）
19. 更新单元测试
20. 构建验证

---

## 附录 A：原框架完整文件清单

### Kotlin 源文件（21 个 main + 3 个 test）

| 文件 | 类 | 说明 |
|------|---|------|
| MainActivity.kt | MainActivity | 宿主 Activity |
| DeepSeekFragment.kt | DeepSeekFragment | DeepSeek 页 |
| ApiKeyFunFragment.kt | ApiKeyFunFragment | APIKEY.FUN 页 |
| WorkbenchFragment.kt | WorkbenchFragment | 工作台页 |
| SettingsFragment.kt | SettingsFragment | 设置页 |
| DeepSeekWidgetProvider.kt | DeepSeekWidgetProvider | 小组件 Provider |
| api/DeepSeekApiClient.kt | DeepSeekApiClient | API 客户端 |
| api/BalanceResponse.kt | BalanceResponse | DeepSeek 余额 DTO |
| api/ModelsResponse.kt | ModelsResponse | 模型 DTO（未使用） |
| api/ApiKeyFunUsageResponse.kt | ApiKeyFunUsageResponse | 用量 DTO |
| api/BalanceDeltaAggregator.kt | BalanceDeltaAggregator | 余额差值聚合 |
| api/DeepSeekUsageAggregator.kt | DeepSeekUsageAggregator | 手工记账聚合 |
| api/UsageComparison.kt | UsageComparison | 日期归一化工具 |
| data/AppPreferences.kt | AppPreferences | DataStore 封装 |
| data/BalanceSnapshot.kt | BalanceSnapshot | 快照模型 |
| data/DeepSeekUsageEntry.kt | DeepSeekUsageEntry | 手工记账模型 |
| ui/UsageDashboardView.kt | UsageDashboardView | 仪表盘视图 |
| ui/UsageTrendView.kt | UsageTrendView | 趋势图视图 |
| ui/WidgetUiHelper.kt | WidgetUiHelper | 小组件 UI 工具 |
| workbench/WorkbenchModule.kt | WorkbenchModule + Registry | 模块定义与注册 |
| workbench/WorkbenchAdapter.kt | WorkbenchAdapter | 模块网格适配器 |
| worker/WidgetUpdateWorker.kt | WidgetUpdateWorker | 后台刷新 Worker |

### 布局文件（9 个）

activity_main · fragment_deepseek · fragment_apikey_fun · fragment_settings · fragment_workbench · item_workbench_module · view_usage_dashboard · widget_balance · dialog_add_usage（死代码）

### 测试文件（3 个）

DeepSeekApiClientTest（8 个测试） · DeepSeekUsageAggregatorTest（3 个） · UsageComparisonTest（2 个）

---

*LUMA Architecture v1.0 — 2026-08-03*
