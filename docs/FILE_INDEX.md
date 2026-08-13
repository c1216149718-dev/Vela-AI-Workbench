# Vela 文件目录索引

更新日期：2026-08-12

## 快速入口

- 当前源码：`app/src/`
- 当前可安装 APK：GitHub Release `v1.20.0` 附件；本地归档仍为 `artifacts/apk/debug/Vela-1.20.0-debug.apk`
- 所有历史 APK：仅保存在本机 `artifacts/apk/debug/`，不进入公开 Git 仓库
- 当前交接报告：`docs/HANDOFF.md`
- App 图标母版：`design/icon/v1.19.0/`
- 1.20.0 侧栏验收截图：`design/validation/v1.20.0/`
- 历史设计方案与验收证据：仅保存在本机 `design/concepts/`、`design/validation/` 的历史目录
- UI 层级转储与构建日志：仅保存在本机 `artifacts/`，不进入公开 Git 仓库

## 目录结构

```text
DeepSeekWidget/
├─ app/                              Android 应用模块
│  ├─ src/                           正式源码、资源与测试
│  ├─ schemas/                       Room 导出 schema
│  └─ build/                         Gradle 自动生成；可重新构建，不作为归档
├─ artifacts/
│  └─ ...                            本机 APK、诊断和日志归档；被 .gitignore 排除
├─ design/
│  ├─ icon/                          图标母版与品牌图形
│  ├─ concepts/                      本机历史设计探索；被 .gitignore 排除
│  └─ validation/
│     ├─ v1.20.0/                    当前侧栏入口、导航与图标验收图
│     └─ 其他目录                    本机历史验收证据；被 .gitignore 排除
├─ docs/                             交接、架构、实现计划与本索引
├─ gradle/                           Gradle Wrapper
├─ AGENTS.md                         研究优先与开发约定
└─ build.gradle.kts 等               构建入口文件
```

## 归档规则

1. 正式 GitHub 版本只把当前 APK 作为 Release 附件上传；`artifacts/` 与 `app/build/outputs/` 都不进入 Git。
2. 新版本验收图放入 `design/validation/v<version>/`，不要直接散放在 `design/` 或 `artifacts/` 根目录。
3. 可继续编辑的设计源稿放 `design/concepts/<主题>/`；正式图标及品牌资产放 `design/icon/`。
4. UIAutomator、布局层级和临时诊断 XML 放 `artifacts/diagnostics/`。
5. 构建日志按日期放 `artifacts/logs/build/<date>/`；崩溃日志放 `artifacts/logs/crash/<date>/`。
6. 不移动 `app/schemas/`、`app/src/androidTest/assets`、Gradle Wrapper 或 `app/build/outputs` 的生成规则，以免破坏迁移测试和构建。
