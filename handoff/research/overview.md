# 项目整体架构调研

> 调研日期：2026-09-07（接手会话）

## 调研目的

恢复 KaTpa 项目上下文：技术栈、模块划分、生命周期装配、跨平台策略、当前进行中工作。

## 范围与方法

- 阅读 `README.md`、`build.gradle.kts`、`KaTpaPlugin.java`（入口装配）。
- 并行子任务调研：数据模型与存储层、服务与命令层、UI 交互与网络层。

## 关键发现

- **技术栈**：Gradle Kotlin DSL + ShadowJar，单 JAR 编译 `src/main`（Paper）与 `src/spigot`（Spigot）双 sourceSet（`build.gradle.kts:20-24`）。
- **跨平台**：启动时反射探测 Dialog 类，自动选 Paper 原生 / Spigot Bungee 实现（`InteractionService.java:113-142`）。
- **依赖**：仅内置 Libby，运行时按 `storage.type` 下载 JDBC（`KaTpaPlugin.java:74-90`）。
- **模块化**：`modules.<name>.enabled` 控制服务创建与命令注册（`KaTpaPlugin.java:281-364`）。
- **存储**：单连接共享模式，SettingsStore 持有物理连接。

## 结论与建议

- 项目架构清晰、模块边界明确，适合增量开发。
- 进行中的跨服吟唱重构（见 services.md 待办）应优先验证跨服中断回滚。
- 新增 placeholder 模块与文档需尽快提交并补充占位符清单。
