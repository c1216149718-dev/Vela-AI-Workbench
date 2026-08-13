# Vela（DeepSeekWidget）交接报告

更新日期：2026-08-13 10:52（Asia/Shanghai）

当前状态：**Verified / public / online-verified**。`1.20.0` 已完成 E2 右缘休眠入口、C1 显式侧栏导航和 I1 统一线性图标；54 项 JVM 单测、Lint、23 项 Android 16 连接设备测试、APK 结构、点击/拖动/关闭动画及浅深色视觉回归均通过。源码已推送到公开 GitHub 仓库 `c1216149718-dev/Vela-AI-Workbench`，GitHub Actions 已通过，`v1.20.0` Release 与 APK 已公开发布并从公网重新下载核验。真实 APIKEY.FUN 多 Key 数据仍需用户凭据做最终联网验收。

GitHub Actions 首轮在 Linux runner 解析 KSP plugin marker 时失败；根因是 Aliyun 镜像排在官方插件仓库之前。修复后 Google、Maven Central 和 Gradle Plugin Portal 优先，Aliyun 仅作回退，Actions 也更新到 Node 24 兼容主版本。提交 `baf4ab6` 的 Linux CI 已在 6 分 10 秒内完成单测、Lint 和 debug 构建并通过。

第一优先级：用真实 DeepSeek/APIKEY.FUN 凭据在真机核对 7/14/30/90 天金额、请求、Token 与平台官网一致，并继续复核 SceneView/Filament 长时间运行、暂停恢复和省电模式。

## 1. 唯一工作目录

正式源码：`D:\CodexProjects\DeepSeekWidget`

只读归档：`D:\CodexProjects\DeepSeekWidget-work-archive`

后续开发只能从正式源码继续。归档目录不参与 Gradle 构建，也不要把历史 APK、快照或旧源码复制回正式目录。

公开仓库：`https://github.com/c1216149718-dev/Vela-AI-Workbench`，默认分支 `main`。`v1.20.0` 标签指向 `baf4ab66769dbd4315261c6c5056b43e3e2637af`；Release 为非草稿、非预发布，APK 公网下载摘要与本地一致。

### 文件分类与快速查找

完整索引见 `docs/FILE_INDEX.md`。当前约定如下：

```text
app/src/                              正式源码与资源
app/build/outputs/                    Gradle 临时构建输出，不作为版本归档
artifacts/apk/debug/                  v1.6.0—v1.20.0 全部 debug APK
artifacts/diagnostics/ui-dumps/       UIAutomator/窗口层级 XML
artifacts/logs/build|crash/           构建与崩溃日志
design/icon/                          正式图标母版
design/concepts/                      历史设计探索与渲染稿
design/validation/<version>/          按版本整理的验收截图
docs/                                 交接、架构、实现计划与文件索引
```

新增 APK 不再放入 `app/releases` 或 `artifacts` 根目录；验收图不再散放在 `artifacts`。每次发布候选都应建立版本目录或使用统一 APK 归档目录，并同步更新 `docs/FILE_INDEX.md` 与本报告。

2026-08-12 目录整理核验：共 23 个历史/当前 debug APK 已统一归入 `artifacts/apk/debug/`；设计概念稿、版本验收图、UI 层级转储、构建日志和崩溃日志均已按上述类别归档。当前 `Vela-1.20.0-debug.apk` 和 v1.20.0 侧栏验收图均已落入对应分类目录；陶瓷图标母版继续位于 `design/icon/v1.19.0/`。

## 2. 当前版本与产物

- applicationId：`com.deepseek.widget`
- 应用显示名称：`Vela`（applicationId 与数据库名不变，覆盖安装保留本地数据）
- versionName：`1.20.0`
- versionCode：`25`
- minSdk / targetSdk / compileSdk：`26 / 36 / 36`
- 最终 debug APK：`artifacts/apk/debug/Vela-1.20.0-debug.apk`
- APK 大小：`76,410,097` bytes
- APK SHA-256：`E32AA68A8E1F426C11525FDFC1E2FEB80CC08679B6E28AE7E9387DAAD0AC3697`
- APK 使用 Android debug 证书和 v2 签名，只用于开发验证，不是商店发布包。

## 3. 2026-08-12 1.20.0 E2 边缘入口、C1 导航稳定性与 I1 图标

### 用户可见结果

- **E2**：删除顶部固定侧栏按钮，改为首页右缘垂直居中的 48 × 56dp 陶瓷入口。休眠时向屏外偏移 35dp、仅露出边缘；触碰立即弹出，点击或向左拖过 24dp 均可打开工具面板，1.8 秒无操作后回缩。入口在二级页面隐藏，避免遮挡内容。
- 入口提供完整 48dp 可访问点击区和“打开 Vela 工具”语义；Android 10+ 只为该小区域申请系统手势排除，避免与全屏返回手势竞争。触摸期间禁止父 `DrawerLayout` 抢占事件，修复首次实现中“点击可开、拖动无效”的设备级问题。
- 关闭系统动画时不运行动画：入口直接显示最终唤醒/休眠状态，工具面板也即时打开，信息和交互均不缺失。抽屉关闭后自动重新进入休眠。
- **C1**：侧栏不再依赖 `NavigationView.setupWithNavController()` 的隐式菜单映射；`MainActivity` 显式校验当前目的地、以 `launchSingleTop` 导航、同步选中项并在成功后关闭右侧抽屉。六个入口——开始专注、专注历史、每日留言墙、DeepSeek、APIKEY.FUN、密钥管理——均纳入自动化回归，跳转不再闪退。
- **I1**：六个侧栏图标统一为 24dp、圆角端点、1.5dp 左右的 Apple 风线性图标；DeepSeek 和 APIKEY.FUN 使用各自可识别的单色线稿，密钥、计时、历史和留言墙使用同一笔触语言。选中态使用品牌蓝，其余使用次级文字色，浅色与深色主题一致。

### 验证证据

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest`：全部成功；JVM 单测 54/54，Android 16 `medium_phone` 连接设备测试 23/23。
- `ComposeShellJourneyTest.rightToolDrawerDestinationsNavigateAndCloseWithoutCrash` 逐一打开右侧抽屉并跳转六个目标，断言目的地正确、抽屉已关闭且无异常。
- Android 16 模拟器设备检查：休眠边缘、点击开启、向左拖动开启、浅色、深色和 `animator_duration_scale=0` 均通过；清空后的 logcat 未发现目标 `FATAL EXCEPTION` 或应用 ANR。截图位于 `design/validation/v1.20.0/`。
- `aapt dump badging`：`versionName=1.20.0`、`versionCode=25`、包名 `com.deepseek.widget`、`minSdk=26`、`targetSdk=36`。
- `apksigner verify`：v2 签名有效，1 个 Android Debug signer；证书 SHA-256 为 `d353323404bacc665b8d128a5a5040c5d4bb4ee9b6ef8e0f7685ba24aea55729`。
- 公开发布前移除未引用的 Jupiter、Saturn、67P 历史模型并重建；最终 APK SHA-256 为 `E32AA68A8E1F426C11525FDFC1E2FEB80CC08679B6E28AE7E9387DAAD0AC3697`。当前 LUNA、ARES、EUROPA 三种主题和 23 项连接测试均通过。

### 风险与后续验证

- **Unverified**：目前只在 Android 16 Pixel 模拟器验证；不同厂商对边缘返回手势、触摸热区和显示裁切可能有差异。下一步应在至少一台三键导航真机和一台手势导航真机复测“休眠 -> 触碰 -> 拖动 -> 抽屉 -> 六入口”。
- 首次冷启动会加载既有 SceneView/Filament 与数据层，模拟器偶尔需要较长时间才呈现首页；本轮没有扩大启动链路。不要在启动页尚未完成时把自动手势注入造成的系统返回或超时误判为侧栏崩溃。

## 4. 2026-08-12 1.19.0 图表稳定性、模型分布与陶瓷品牌更新

### 用户可见结果

- 修复用量详情快速切换 7/14/30/90 天时的闪退。根因是 Vico 过渡帧仍请求旧横轴索引，而日期数组已切成更短范围，格式器返回空字符串并触发 `IllegalStateException`。当前横轴使用 `LocalDate.toEpochDay()` 的稳定坐标，格式器始终返回非空日期；刷新改为捕获选定周期、取消旧任务并保证最后一次选择生效。
- 趋势图高度收紧为 188dp、线宽提升到 3dp，并禁用默认横向滚动，使 14/30/90 天完整范围适配卡片；长按/拖动标记与屏幕阅读器日期序列仍保留。费用、请求和 Token 切换继续共享同一日期范围。
- 每日精确值改为默认折叠的“数据明细”；模型区改为环形占比图配紧凑排行。默认前 5 名，余项合并为“其他”且可展开；单模型可点开查看平台、币种、费用、请求和 Token。费用视图在存在多币种时先按币种切换，避免把 CNY 与 USD 错误相加；请求和 Token 可跨平台聚合。
- 洞察、专注和设置中的冗长常驻说明已缩短；设置页品牌名统一为 Vela。关闭系统动画时仍直接呈现完整图表、折叠入口、精确值和可访问性语义。
- Launcher 图标改为真正的双层 Adaptive Icon：米白陶瓷纹理底层、粗糙磨砂双帆与轨道前景，并补齐 monochrome 图层。用户参考、生成母版和透明前景统一归档在 `design/icon/v1.19.0/`；Android 16 圆形蒙版验收图位于 `design/validation/v1.19.0/v1.19.0-launcher.png`。
- 使用 AndroidX SplashScreen 增加约 1 秒的原生启动动效：轨道旋转、帆体轻抬、底部涟漪；系统关闭动画时不强制等待，静态最终图形与全部应用信息仍可用。Android 12 以下兼容层以静态图形回退。
- 原左侧抽屉改为右侧 Vela 工具面板；顶部入口从悬浮汉堡改为低阴影陶瓷工具矩阵图标。底部四个主入口不再在面板内重复，面板只收纳专注、留言墙、数据源和密钥管理；右缘滑动、遮罩点击和系统返回均可关闭。

### 验证证据

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest`：全部成功；JVM 单测 54/54，Android 16 `medium_phone` 连接设备测试 22/22。
- 动画开启与关闭分别连续循环点按 7/14/30/90 天 10 轮，共 80 次切换；logcat 无 `FATAL EXCEPTION`、`CartesianValueFormatter` 或目标 `IllegalStateException`。布局证据为 `design/validation/v1.19.0/rapid-range-animations-*-layout.json`。
- `aapt dump badging`：`versionName=1.19.0`、`versionCode=24`、`minSdk=26`、`targetSdk=36`、label=`Vela`。
- `apksigner verify`：v2 签名有效，1 个 Android Debug signer；证书 SHA-256 仍为 `d353323404bacc665b8d128a5a5040c5d4bb4ee9b6ef8e0f7685ba24aea55729`。
- 视觉证据位于 `design/validation/v1.19.0/`，覆盖 Launcher、启动页、首页、洞察、用量详情上下区和右侧工具面板；每张截图均已人工检查。
- 未验证项：模拟器没有真实平台凭据，环形图的空态和组件结构已验收，但真实多 Key 前五名/其他展开、跨币种切换与平台官网逐字段数值仍需用户凭据在真机确认。

## 5. 2026-08-11 1.18.0 Vela、用量中心、留言墙与侧栏

### 用户可见结果

- 应用显示名从“AI 工作台”改为 **Vela**，`applicationId=com.deepseek.widget` 保持不变；新自适应图标保留银色帆体、深海军蓝底、蓝青轨道、星芒和三段信号弧。母版位于 `design/icon/vela-icon-master-1536.png`，Android 16 Pixel Launcher 圆形蒙版截图位于 `design/validation/v1.18.0/v1.18.0-launcher.png`。
- 洞察首页以“总用量”为主卡，按币种分别显示人民币估算与美元实扣；DeepSeek/APIKEY.FUN 平台小计为次级信息。不同币种不换算、不相加。
- 点击总用量进入详情：日期范围为 7/14/30/90 天；费用、请求、Token 切换共用同一个日期状态；DeepSeek 与 APIKEY.FUN 分别显示 Vico 动态折线图、触摸标记与图下逐日精确文本清单。
- 14 天以上图表日期改为均匀稀疏标签，旧 `UsageTrendView` 同步修复尾部相邻标签重叠。系统动画关闭时直接呈现完整最终数据，不隐藏图表或文字。
- 模型排行默认保留前 5 名，其余合并为“其他”；仍按 Key 别名显示模型来源。费用尾项若混有不同币种，会标为“分币种合并”而不是伪造金额。
- APIKEY.FUN 用量不再只存在于供应商页面内存；按 Key、日期、周期模型维度写入 Room。刷新失败保留上次成功缓存并显示 partial/stale 状态，最后成功刷新时间单独展示。
- DeepSeek 刷新调用官方余额接口，保存余额快照并以相邻快照下降估算每日费用；官方接口不提供请求、Token 和模型明细，因此这些字段只在本地账本已有数据时显示，不能伪造。
- 复盘归档改为按月分组的双列纸签式“每日留言墙”，支持搜索、评分色调和详情弹层，数据仍只保存在本机。
- 手机保留 4 个底部主入口并增加模态侧栏；侧栏收纳专注、专注历史、留言墙、数据源和密钥管理。`smallestScreenWidthDp >= 600` 时主入口切换为侧边轨道。
- F3 语义保持不变：不足 5 分钟的专注不会保存；时长设置页和空历史现在明确说明这一规则。

### 数据与迁移

- Room 当前版本为 **4**（旧文档中 v2/v3 说法已过期）。v3 -> v4 新增 `ai_usage_model_period` 与 `ai_usage_sync_state`，保留原 `ai_usage_daily` 和所有任务/专注/复盘数据。
- `AiUsageRepository` 是洞察的共享数据入口：APIKEY.FUN 每次最多并发 3 个 Key，成功结果按凭据替换同范围缓存；失败只更新错误状态，不删除旧数据。
- 关键口径：金额用十进制字符串持久化并用 `BigDecimal` 汇总；密钥明文不写入 Room、日志或界面状态；区间模型汇总不伪装成单日数据。

### 验证证据

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`：成功。
- Android 16/API 36 模拟器：`WorkbenchMigrationTest` 4/4 通过，覆盖 1->2、2->3、3->4 数据保留与新表写入。
- `aapt dump badging`：`versionName=1.18.0`、`versionCode=23`、`minSdk=26`、`targetSdk=36`、label=`Vela`。
- 视觉证据统一位于 `design/validation/v1.18.0/`：首页、洞察、用量详情、抽屉和 Launcher。
- 未验证项：仓库没有可用于自动化验收的真实平台密钥，因此 APIKEY.FUN 多 Key 聚合数值与平台官网的逐字段对账仍待真机凭据验收；不应把模拟器空态当成线上数据验收完成。

## 6. 2026-08-11 1.17.0 专注流程与 SceneView 稳定性

### 当前用户可见行为

- **Verified**：专注配置拆为“时长选择 -> 天体主题预览 -> 沉浸计时”三段，时长范围为 `5..300` 分钟，预设为 25、60、120 和 300 分钟。
- **Verified**：天体主题为 `LUNA/月面静默`、`ARES/火星暮线`、`EUROPA/冰原轨迹`，分别加载 `moon.glb`、`mars.glb`、`europa.glb`。来源与署名见 `app/src/main/assets/models/ATTRIBUTION.txt`。
- **Verified**：月球主题暂停或结束后切换火星不再叠加两个模型。设备截图覆盖独立时长页、月球预览、火星预览、月球运行和月球暂停。
- **Verified**：专注不足 5 分钟时，完成或取消都会删除活动会话，不写入历史；暂停中的未结束暂停段不计入实际专注分钟。
- **Verified**：日程分钟钟盘中央显示刚刚选择的小时，点击中央可返回小时钟盘；只增加该交互，没有改动双环钟盘的视觉结构。

### 3D 重叠根因与最终方案

重叠不是贴图问题，而是 SceneView 生命周期边界错误：多个 `ModelInstance`/`ModelNode` 曾同时存活，根节点可见性没有可靠地传播到 Filament 实体。最终实现位于 `feature/focus/PlanetariumScene.kt`：

- `rememberModelInstance(modelLoader, modelPath)` 放在 `SceneView` 子作用域内；
- 用 `key(style)` 让主题变化时销毁旧子树，再创建当前模型；
- 任一时刻只存在一个选中主题的 `ModelNode`；
- `activeNode` 同时携带主题标识，旋转更新只作用于当前主题节点。

不要恢复以下失败方案：

- 仅切换 Compose 根容器 `isVisible`：不会保证底层 Filament 实体不可见；
- 对模型设置 Filament layer mask：当前 Android 16 模拟器上曾导致全部模型空白；
- 同时预加载三套模型再切换显示：会显著增加 GPU/内存压力，曾触发 ANR 和模拟器退出。

### 计时状态与持久化

- `FocusRepository.start()` 强制 `plannedMinutes in 5..300`；UI 与 ViewModel 使用同一范围。
- `complete()` / `cancel()` 先读取活动会话并计算真实专注时间；小于 `300_000 ms` 时调用 `FocusSessionDao.deleteActive()`，否则落为完成或取消状态。
- `FocusSession.actualMinutes()` 从总时长中扣除已累计暂停和当前未结束暂停，工作台与历史页都复用这一口径。
- 新增测试覆盖运行中短会话、暂停中短会话、负数保护、未恢复暂停和 DAO 删除活动会话。

## 7. 2026-08-10 专注、日程窗口与导航栏验收（历史基线）

### 1.14.0 本轮完成范围

- **Verified**：本轮只完善专注页、日程安排和底部导航；多 Key、每日复盘与全局 A+C 配色保持既有实现，没有重复重构。
- 首页放大后的专注行现在整行跳转，离开首页前会关闭 Mac 窗口；返回首页后不会残留窗口或继续锁住底部导航。
- 专注页已改为 Compose 天体仪：SceneView 4.1.1 + Google Filament 实时渲染 NASA 木星、NASA 土星和 ESA 67P 彗星模型。三种模型可在设置态预览，开始后同一场景扩展为暗夜全屏，系统栏隐藏，样式锁定。
- 专注倒计时支持点击天体区域显示/隐藏数字、暂停/继续、完成和取消；暂停时星空环境速度与亮度降至约 25%。Android 16 模拟器已验证 3D 出帧、星空覆盖、全屏过渡和退出恢复，无 Filament 或 AndroidRuntime 崩溃。
- `TODAY` 映射为待办，`PLANNED` 映射为日程。日程区间允许跨午夜，但必须 `end > start` 且总时长不超过 24 小时。
- 时间编辑使用同一 Mac 窗口状态机：`COMPACT` 为 24 小时/分钟滚轮，`STANDARD` 为外环 00-11、内环 12-23 的双环钟盘，`EXPANDED` 为滚轮与月历重排，`FULLSCREEN` 为无圆角全屏月历。红点关闭、黄点逐级缩小、绿点逐级放大。
- 时间窗口使用 46dp 模糊和高乳化玻璃染色，保留底层模糊轮廓但不再让标题、优先级和日程文字穿透干扰阅读。
- 底部导航收窄为 64dp、24dp 圆角、22dp 图标；选中镜片为 46 x 38dp，仅包围图标并平滑滑动。Material 自带选中底板已关闭，不再叠加旧浅色条。

### 官方 3D 资产

- **Superseded by 1.17.0**：旧版曾打包 NASA Jupiter、NASA Saturn 和 ESA 67P 模型；当前运行时只加载 LUNA、ARES、EUROPA 三个主题。公开发布前已将三份未引用旧资产移出正式源码，避免继续增加 APK 体积和不必要的再分发边界。
- 来源与署名记录在 `app/src/main/assets/models/ATTRIBUTION.txt`。

## 8. 2026-08-09 Compose 新壳、真实玻璃材质与小组件验收

### 1.11.0 真实玻璃、窗口状态与液态底栏

- **Verified**：首页展开窗口使用独立 Haze 页面采样层，不再只模糊装饰背景。窗口采用 Cupertino `ultraThin`、24dp 模糊和低染色，浅色与深色截图中均可辨认底层专注蓝色和账户卡轮廓的真实模糊色块。
- 浅色基底由纯白改为偏暖的雾面象牙色，Compose 与 XML 迁移层共用 `#E8E4DB` 背景；卡片保留白色高光边，蓝、绿和陶土状态色维持综合色彩层次。
- 首页窗口改为 `COMPACT -> WINDOWED -> FULLSCREEN` 三档。红点始终关闭；黄点逐档缩小并在紧凑态关闭；绿点逐档放大并在全屏禁用；系统返回键按相同顺序回退。
- 全屏尺寸、圆角和底栏均使用约 360ms 过渡。窗口打开时全屏遮罩消费底层指针，底层卡片和底栏菜单不可交互；全屏时底栏与选中胶囊向下滑出。
- 底栏采用方案 C：独立液态玻璃选中胶囊包住图标与标签，跨页时横向拉伸、压缩并回弹。实机截图确认首页与洞察终点均居中，快速连续切页会取消上一段动画。
- DeepSeek 与 APIKEY.FUN 输入框继续视觉遮盖，但移除密码输入法变体。系统验证输入类型为普通文本 `0x80001`，控件 `long-clickable=true` 且 `password=true`，因此保留长按和输入法粘贴，同时不直接明文显示 Key。
- 视觉证据位于 `design/validation/v1.11.0`，包括浅色/深色首页、普通窗口、深色全屏和洞察底栏终点截图。

### 架构边界

- 保留已经验证的 API 客户端、兼容解析器、Room 数据库、Repository、DataStore、WorkManager、通知和 RemoteViews 小组件核心。
- **Superseded by 1.14.0**：当时 Compose 只覆盖四个一级页面；当前任务编辑和专注主体也已改为 Compose，账户配置仍由 Fragment 壳承载。
- 主导航收敛为“首页、任务、洞察、设置”四个入口。DeepSeek、APIKEY.FUN、专注和任务编辑均作为二级页面进入。
- 迁移期继续使用 Fragment NavHost 管理 Compose 与 XML 混合页面；其余 Fragment 完成替换后再统一切换 Navigation Compose，避免维护两套顶层导航状态。

### Compose 页面与视觉系统

- **Verified**：1.10.0 将四个一级页面统一为 Apple Productivity 方向，参考 Health 的数据阅读层级、Reminders 的任务分组和 Wallet 的账户摘要，但保留 Android 导航、返回和触控规则。
- 首页从“大钟 + 描边区块”改为大标题、今日摘要、无描边快速新增、分组任务列表、蓝色专注入口、Wallet 式双账户行和今日复盘。
- AI 洞察改为大金额主指标、`近 7 天` 周期标记、圆柱图/明确空状态、双数据源分组和可信度说明。
- 主题新增浅色与暗色 `glassSurface` / `glassBorder` 语义色。首页与洞察主分组使用单层半透明表面和细亮边，暗色采用石墨玻璃层，避免叠层阴影造成的内部白块。
- Android 12+ 的 Compose 卡片使用 Haze Cupertino material 做实时背景模糊，Fragment 壳层的悬浮底栏使用 BlurView 硬件模糊；Android 12 以下保留静态半透明回退，但不作为本轮 95% 视觉相似度验收基线。
- **Superseded by 1.14.0**：当时底栏为 82dp；当前已收窄为 64dp，并使用仅包围图标的 46 x 38dp 滑动镜片。
- 设置页新增“跟随系统 / 浅色 / 深色”手动主题选择，选择后通过 DataStore 持久化并重建 Activity。
- **Superseded（1.10.0）**：旧版窗口只有普通/最大化两档，黄点直接关闭、绿点切换最大化；该行为已由上文 1.11.0 三档状态机和液态底栏动画取代。
- 应用图标替换为白底、深色分段环形电路和蓝色核心的自适应图标，并在 Android 16 启动器圆形蒙版和小组件选择器中实机验收。
- 首页和洞察页水平留白增至 20dp，主区块间距增至 32dp；标题突出中文主语义，以 `TODAY`、`INSIGHTS`、`One thing at a time.` 等短英文建立节奏，删除解释性长文案。
- 已在 Android 16 检查浅色、暗色和 1.3 倍系统字体，长文案可换行，金额、按钮和账户行无遮挡。
- 为旧 XML 页面补齐深色资源，修复 API Key 输入框和用量区间在深色模式下浅底浅字的问题。

### 桌面小组件

- RemoteViews 接口保持不变，尺寸改为 `3 x 2`，最小 `180dp x 120dp`，支持横向和纵向调整。
- **Verified**：Pixel Launcher 组件选择器显示真实 Wallet 式预览和 `3 x 2` 尺寸，实际桌面已成功添加并保持 RemoteViews 更新。
- 主体改为上下两行账户结构，使用 DeepSeek 与 APIKEY.FUN 官方图标、右对齐余额、独立状态、DeepSeek 今日本地费用、整体健康状态和刷新时间。
- RemoteViews 使用半透明冷白/石墨背景、细亮边和半透明按钮模拟系统玻璃层；受平台限制不宣称实时壁纸模糊。
- 账户行按可用高度均分，在 Launcher 预览和实际桌面均无中部异常空白；浅色与暗色资源会随系统模式切换。
- 根卡、两个账户区域和刷新按钮均已在 Android 16 模拟器实测；未配置、正常、部分失败和余额警告仍可渲染，不因单一数据源失败造成整张组件加载失败。

## 9. 2026-08-05 审查与修复结果

### 启动、导航与构建

- `MainActivity` 直接从 `NavHostFragment` 获取 `NavController`，不再使用首次布局后的 `post` 时序补丁。
- BottomNavigationView 使用 `setupWithNavController`，根页面保留状态；任务、专注等子页面隐藏底栏并提供明确返回按钮。
- Intent 深链在消费后移除 extra，避免 Activity 重建时重复导航。
- 删除会替换默认异常处理器的 `CrashReporter`、独立 `:crash` 进程和红屏 `CrashActivity`。
- Manifest 关闭明文流量，移除应用自身未使用的前台服务声明；合并 APK 中的 WorkManager 权限与组件来自依赖库。
- 删除每次 assemble 自动复制时间戳 APK 的 Gradle 任务，避免 `app/releases` 持续产生重复文件。
- Gradle Metaspace 调整为 768 MiB；完整 lint、单测和 AndroidTest 打包不再触发 Metaspace 耗尽。

### 任务与提醒

- 修复任务编辑器未监听标题/备注、状态回流覆盖输入、新建任务丢失备注与优先级的问题。
- 字段现包含：标题、备注、优先级、计划日期、截止时间、提醒时间、预计分钟；新任务默认进入“收件箱”项目。
- 标题限制 1 至 120 字，备注最多 4000 字，预计时长 1 至 1440 分钟。
- 修改计划日期时会在 `BACKLOG` 与 `PLANNED` 间归一化，`IN_PROGRESS`、`DONE`、`CANCELLED` 状态保持语义。
- 保存/修改提醒会安排唯一 Work；完成、删除或清除提醒会取消旧 Work。Worker 使用预期时间戳原子消费提醒，旧 Worker 不会清除后来修改的新提醒。
- 删除增加二次确认；任务列表支持参数化筛选和标题/备注搜索，FAB 以叠加方式显示，不再挤压列表。

### 专注计时

- DAO 事务保证同一时间最多一个 `RUNNING`/`PAUSED` 会话。
- 新会话只有在完成 Worker 安排成功后才保留；调度失败会回滚会话，避免产生无法后台结束的孤立计时。
- 暂停会取消完成 Worker；继续会顺延 `expectedEndAt` 并按剩余时间重新安排。
- 完成 Worker 只允许把 `RUNNING` 会话改为 `COMPLETED`；暂停会话不会被后台误完成。
- 开始关联任务的专注时，任务自动进入 `IN_PROGRESS`。
- 页面支持 15/25/45/60 分钟、自定义 1 至 1440 分钟、关联未完成任务，以及完成专注时可选同步完成任务。
- Android 13 及以上在开始专注时请求通知权限；通知点击可进入相应任务或专注页。
- 历史和工作台按实际结束时间减去暂停时长统计，不再把提前完成的会话按预设分钟累计。

### 工作台与 UI

- 工作台使用 `ListAdapter + DiffUtil + stable IDs`，不再用 `notifyDataSetChanged` 整页刷新。
- 首页包含：日期问候、快速新增、今日任务及完成比、当前专注、DeepSeek/APIKEY.FUN 余额摘要、今日复盘、任务/专注入口。
- 工作台在返回前台时刷新日期和问候，跨午夜不会继续显示昨天数据。
- 应用与小组件改为中性白灰、陶土强调色、低饱和蓝绿状态色；卡片统一 8dp，图标同步新配色。
- 用量图表保留费用/请求/Token 对比和系统动画关闭时的静态回退。

### APIKEY.FUN

- 余额响应继续由手动兼容解析器转换为 `BalanceResponse`，不会再用 DeepSeek 的严格响应结构直接反序列化。
- 用量错误页不再展示最多 500 字的原始响应，避免 UI 污染和账户信息泄露。
- 当响应同时含单模型摘要和完整 `model_stats` 时，解析器选择模型条目最多的候选，避免只显示 Claude。
- `ApiKeyFunUsageAggregator` 会按日期和模型聚合所有启用 Profile 的成功结果；每批最多 3 个 Key，单 Key 失败不取消其他 Key。
- 多 Key 部分失败会显示可恢复警告；下一次全部成功会清除旧警告，连接测试在网络或本地存储失败后也会恢复按钮状态。
- 余额始终只取 `isPrimaryForBalance` 主 Key，不会把同一账户多个 Key 的余额相加。

### 桌面小组件（历史基础）

- RemoteViews 只使用白名单控件，并已通过 `RemoteViewLayout` lint 检查。
- 根卡打开工作台；DeepSeek 与 APIKEY.FUN 区域分别打开对应页面；刷新按钮独立触发即时 Worker。
- 读取缓存异常时仍渲染可加载的未配置状态，不让 Receiver 异常导致 Launcher 加载失败。
- 余额格式化不再共享线程不安全的 `DecimalFormat`。
- 重复且余额未变化的 DeepSeek 快照不再写入 DataStore，避免长期后台刷新造成存储膨胀。
- 后续已在 2026-08-07 调整为最小 `180dp x 120dp`、目标 `3 x 2`，支持横向和纵向调整。

## 10. DeepSeek 数据口径

2026-08-05 再次核对 DeepSeek 官方资料：公开 API 文档提供 `GET /user/balance`；按 API Key 的详细历史用量仍要求在 Usage 页面按月导出 CSV，未文档化历史用量查询 API。

- 官方余额文档：<https://api-docs.deepseek.com/api/get-user-balance/>
- 官方按 Key 用量说明：<https://api-docs.deepseek.com/faq#how-to-view-usage-by-api-key>

因此当前应用只把余额快照下降量作为费用估算。它不能恢复请求数、Token 或模型分布，也不能区分充值、赠金调整与两次快照之间的全部计费事件；界面不得把这些未知指标显示为精确官方用量。

## 11. 关键源码

```text
app/src/main/java/com/deepseek/widget/
├─ MainActivity.kt                         导航与 Intent 深链
├─ DeepSeekWidgetApp.kt                    Application 与容器初始化
├─ AppContainer.kt                         手动依赖注入
├─ DeepSeekFragment.kt                     DeepSeek 余额与扣减估算
├─ ApiKeyFunFragment.kt                    APIKEY.FUN 余额、多 Key 用量聚合
├─ DeepSeekWidgetProvider.kt               AppWidget Receiver 与点击入口
├─ api/
│  ├─ DeepSeekApiClient.kt                 两个供应商的 HTTP 与容错解析
│  └─ ApiKeyFunUsageAggregator.kt          多 Key 按日期/模型聚合
├─ data/
│  ├─ AppPreferences.kt                    账户缓存与余额快照
│  ├─ ApiKeyFunProfileStore.kt             多 Key Profile 与主 Key
│  ├─ local/                               Room v4、Entity、DAO、Migration
│  └─ repository/                          任务、专注、复盘、AI 用量 Repository
├─ feature/
│  ├─ home/                                Compose 首页
│  ├─ insights/                            Compose AI 洞察、Vico 图表与用量详情
│  ├─ workbench/                           首页状态与兼容入口
│  ├─ settings/                            Compose 设置页
│  ├─ tasks/                               Compose 任务列表、任务编辑和 Mac 时间窗口
│  ├─ focus/                               Compose 3D 专注计时和历史
│  └─ review/                              Compose 每日留言墙
├─ ui/components/                          Haze 玻璃容器与供应商品牌组件
├─ ui/theme/                               浅色/深色主题与玻璃语义色
└─ worker/                                 余额刷新、任务提醒、专注完成、通知
```

Room 当前为 v4，包含 `projects`、`tasks`、`focus_sessions`、`daily_reviews`、`ai_usage_daily`、`ai_usage_model_period` 和 `ai_usage_sync_state`。禁止加入 `fallbackToDestructiveMigration()`。

## 12. 构建与验证

```powershell
Set-Location 'D:\CodexProjects\DeepSeekWidget'
$env:JAVA_HOME = '<本机 JDK 17 根目录>'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --console=plain
```

2026-08-12 `1.20.0` 当前执行/复核结果：

- `testDebugUnitTest`：54/54 通过，0 失败、0 错误。
- `lintDebug`：通过，0 error。
- `assembleDebug`：通过。
- `assembleDebugAndroidTest`：通过。
- `connectedDebugAndroidTest`：Android 16 `medium_phone` 上 23/23 通过。
- `aapt dump badging`：确认 `versionName=1.20.0`、`versionCode=25`、包名 `com.deepseek.widget`、标签 `Vela`。
- `aapt2 dump xmltree`：确认 `initialLayout`、`previewLayout`、`targetCellWidth=3`、`targetCellHeight=2`、横竖缩放声明，以及布局仅含 `LinearLayout`、`TextView`、`ImageView`。
- `apksigner verify`：v2 签名有效，1 个 Android Debug signer；证书 SHA-256 为 `d353323404bacc665b8d128a5a5040c5d4bb4ee9b6ef8e0f7685ba24aea55729`。
- `app/build/outputs/apk/debug/app-debug.apk` 是 GitHub `v1.20.0` Release 附件的来源，SHA-256 为 `E32AA68A8E1F426C11525FDFC1E2FEB80CC08679B6E28AE7E9387DAAD0AC3697`。
- 视觉验收：`design/validation/v1.20.0/` 覆盖右缘休眠、拖动打开、浅色/深色工具面板和关闭系统动画；`design/validation/v1.19.0/` 保留 Launcher、启动页、洞察、用量详情和模型区证据；历史设备证据在 `design/validation/historical/`。
- Launcher 验收：Pixel Launcher 可预览、选择、添加、刷新和点击 `3 x 2` 小组件；实际桌面成功加载双账户状态和官方供应商图标，自适应应用图标裁切正常。

当前验收设备为 Android 16 模拟器，不等同于厂商真机。通知延迟、Doze、厂商后台限制和第二个 Launcher 尚未验证。

## 13. 尚未完成或仍需验证

1. APIKEY.FUN 本轮没有用户真实 Key，无法做在线契约回归；解析器和聚合器使用脱敏 fixture 验证。
2. 用量当前是进入洞察或手动点击刷新时同步；没有增加独立后台用量 Worker。余额 Worker 仍按既有周期运行。
3. 报告、信息收件箱和预算提醒仍是后续阶段，`WorkbenchModuleRegistry.enabledModules` 只开放任务与专注，不得提前展示无行为卡片。
4. API Key 仍保存在普通 Preferences DataStore。公开发布前应迁移到 Android Keystore 支持的加密存储。
5. WorkManager 提醒不是精确闹钟，Doze 或厂商后台策略可能延迟执行；真机验收应覆盖锁屏和省电模式。
6. 已完成 Android 16 模拟器截图级验收和 Pixel Launcher 图标/小组件实测；正式发布前仍需补厂商真机、横屏、第二个 Launcher，以及三键/手势导航下的右缘入口回归。
7. 任务列表、任务编辑、专注、留言墙、洞察和设置的主要界面已迁移到 Compose；账户详情仍保留 Fragment/XML 壳。二级导航仍由 Fragment NavHost 承担。
8. 新图标母版为 1536px PNG；正式上架前仍应导出并验收 Play Store 512px 商店图、品牌矢量源文件和厂商 Launcher 裁切矩阵。

## 14. 后续接手顺序

1. 在手势导航与三键导航真机覆盖安装 `artifacts/apk/debug/Vela-1.20.0-debug.apk`，先验收右缘入口的休眠、触碰、拖动、抽屉关闭与六入口跳转；完成条件是连续 20 轮无误触系统返回、无闪退且所有目的地一致。
2. 用真实多 Key 逐字段对账 APIKEY.FUN 的每日费用、请求、Token、模型与 Key 归属，并检查前五名/其他展开和币种切换；同时核对 DeepSeek 余额快照差值。
3. 验证断网、单 Key 401、部分 Key 失败和恢复联网：旧缓存必须保留，last-success 时间不前移，成功后错误状态清除。
4. 继续复核“LUNA 开始 -> 暂停/完成 -> 返回 -> ARES 预览/开始”，覆盖 Filament 驱动、长时间专注、横竖屏和省电模式。
5. 在第二个 Launcher 验证自适应图标、侧栏入口、小组件添加/缩放/刷新和点击入口。
6. 公开发布前把 API Key 迁移到 Keystore 支持的加密存储，生成 release 签名包、512px 商店图和品牌矢量源文件；debug APK 不可用于发布。

## 15. 文档规范状态

工作区要求先读取 `docs/DOCUMENT_STYLE.md`，但正式项目和上级工作区均不存在该文件。本报告沿用仓库现有 Markdown 风格，并已检查标题层级、代码块、路径、版本号、命令、测试结果和已知限制；无法执行缺失规范中未提供的“最终强制检查”条目。
