# 核心入口与装配（core）

> 最后更新：2026-09-07

## 现状

- 入口类 `KaTpaPlugin.java`（385 行）负责生命周期装配：下载 JDBC 依赖、初始化存储、创建服务、注册指令/监听器、挂载 Vault/PlaceholderAPI。
- 模块开关驱动所有服务与命令的创建（`moduleEnabled(module)`，读 `modules.<module>.enabled`，默认 true）。

## 关键流程

### onLoad（下载 JDBC）
- `KaTpaPlugin.java:74-90`：按 `storage.type` 用 Libby 加载 `sqlite-jdbc` 或 `mariadb-java-client`，库目录取 `../libraries`。

### onEnable（装配顺序）
1. `saveDefaultConfig` + `ConfigUpdater.checkAndUpdateConfig` 自动升级配置（`:97`）。
2. `MessageService`、`SettingsStore`（**始终初始化**，失败即禁用插件，`:101-109`）。
3. `SoundService`、`ParticleService`。
4. `tpa` 模块：`TeleportService` + `RequestService`（`:113-116`）。
5. `InteractionService`（Dialog 平台探测，失败即禁用，`:117-123`）。
6. `CrossServerService`（KaProxy 网络，`:124-125`）。
7. `back`/`dback`：共用 `BackStore`（`:126-139`）。
8. `warp`：`WarpStore` + `WarpService`（`:140-148`）。
9. `home`：`HomeStore` + `HomeService`（`:149-157`）。
10. `setupEconomy`（Vault 可选）、`registerPlaceholders`（PAPI 可选）、`PlayerListener`、`rememberPlayer`。

### onDisable
- 逆序关闭：`network` → `interactions` → `requests` → `teleports` → 各 store → `settings`（统一关连接，`KaTpaPlugin.java:186-197`）。

## 踩过的坑

- SQLite JDBC 默认 compileOnly，运行时必须经 Libby 下载，不能打进 shadowJar。
- 模块化关闭时若不注册 `disabledCommand`，玩家输入会收到 Bukkit 默认 "unknown command"。

## 当前待办

- 工作区改动 `KaTpaPlugin.java`（+11 行）疑似与 placeholder 注册相关，需确认是否需要在此处显式注册 `KaTpaPlaceholderExpansion`（当前 `registerPlaceholders` 已注册，见 `KaTpaPlugin.java:298-304`）。
- `build.gradle.kts` 有 1 行未提交改动，需确认内容。
