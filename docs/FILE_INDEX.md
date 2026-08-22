# Vela 文件目录索引

更新日期：2026-08-21

## 快速入口

- 当前源码：`app/src/`
- 当前本地候选 APK：`artifacts/apk/debug/Vela-1.22.1-debug.apk`；`Vela-1.22.0-debug.apk` 与更早版本同目录保留，最近公开版本仍为 GitHub Release `v1.20.0`
- 所有历史 APK：仅保存在本机 `artifacts/apk/debug/`，不进入公开 Git 仓库
- 当前交接报告：`docs/HANDOFF.md`
- App 图标母版：`design/icon/v1.19.0/`
- 1.21.1 启动页/标题/侧栏可重建设计源：`design/source/v1.21.1/`
- 1.21.1 多比例浅深色设备验收截图与 SSIM 报告：`design/validation/v1.21.1/`
- 1.21.0 上一候选设计源与截图：`design/source/v1.21.0/`、`design/validation/v1.21.0/`
- 1.22.0 供应商官方图标来源清单：`design/provider-logos/SOURCES.md`
- 1.22.0 供应商连接器、签名器与账单解析：`app/src/main/java/com/deepseek/widget/data/provider/`
- 1.22.0 云签名与固定响应测试：`app/src/test/java/com/deepseek/widget/data/provider/`
- 1.22.0 最终 APK 首页/洞察/设置视觉抽查：`design/validation/v1.22.0/`
- 1.22.1 旧花纹基线与结构验证脚本：`design/source/v1.22.1/`
- 1.22.1 高清花纹对照与 SSIM 报告：`design/validation/v1.22.1/`
- 历史设计方案与验收证据：仅保存在本机 `design/concepts/`、`design/validation/` 的历史目录
- UI 层级转储与构建日志：仅保存在本机 `artifacts/`，不进入公开 Git 仓库

## 目录结构

```text
DeepSeekWidget/
├─ app/                              Android 应用模块
│  ├─ src/                           正式源码、资源与测试
│  │  ├─ main/.../data/provider/     十供应商注册、连接器、云签名与账单导入
│  │  └─ test/.../data/provider/     TC3/BCE/Aliyun RPC 与固定响应契约测试
│  ├─ schemas/                       Room 导出 schema
│  └─ build/                         Gradle 自动生成；可重新构建，不作为归档
├─ artifacts/
│  └─ ...                            本机 APK、诊断和日志归档；被 .gitignore 排除
├─ design/
│  ├─ icon/                          图标母版与品牌图形
│  ├─ provider-logos/                十供应商官方图标来源与许可核对清单
│  ├─ source/v1.21.1/                当前批准参考、资源生成/验证脚本和标题总览
│  ├─ source/v1.22.1/                底部花纹旧基线与高清结构验证脚本
│  ├─ source/v1.21.0/                上一候选设计源归档
│  ├─ concepts/                      本机历史设计探索；被 .gitignore 排除
│  └─ validation/
│     ├─ v1.20.0/                    最近公开版侧栏入口、导航与图标验收图
│     ├─ v1.21.1/                    当前候选多比例浅深启动/页面/侧栏截图与 SSIM 报告
│     ├─ v1.22.0/                    十供应商升级后的首页、洞察与设置抽查截图
│     ├─ v1.22.1/                    高清花纹浅深对照与结构 SSIM 报告
│     ├─ v1.21.0/                    上一候选验收截图
│     └─ 其他目录                    本机历史验收证据；被 .gitignore 排除
├─ docs/                             交接、架构、实现计划与本索引
├─ gradle/                           Gradle Wrapper
├─ AGENTS.md                         研究优先与开发约定
└─ build.gradle.kts 等               构建入口文件
```

## 归档规则

1. 正式 GitHub 版本只把当前 APK 作为 Release 附件上传；`artifacts/` 与 `app/build/outputs/` 都不进入 Git。
2. 新版本验收图放入 `design/validation/v<version>/`，不要直接散放在 `design/` 或 `artifacts/` 根目录。
3. 可继续编辑的探索稿放 `design/concepts/<主题>/`；可重建的发布视觉源放 `design/source/<version>/`；正式图标及品牌资产放 `design/icon/`。
4. UIAutomator、布局层级和临时诊断 XML 放 `artifacts/diagnostics/`。
5. 构建日志按日期放 `artifacts/logs/build/<date>/`；崩溃日志放 `artifacts/logs/crash/<date>/`。
6. 不移动 `app/schemas/`、`app/src/androidTest/assets`、Gradle Wrapper 或 `app/build/outputs` 的生成规则，以免破坏迁移测试和构建。
