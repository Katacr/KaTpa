# 配置与语言（config）

> 最后更新：2026-09-15

## 现状

- 功能参数 `config.yml`（`src/main/resources/config.yml`），玩家消息/界面文本 `lang/zh_CN.yml`（通过 `config.language` 选择）。
- **配置自动升级**：`ConfigUpdater.checkAndUpdateConfig`（`util/ConfigUpdater.java`，`CURRENT_CONFIG_VERSION=3`）：备份旧文件 → 从 JAR 提取默认 → 回写用户值（跳过 `config-version` 与不存在的键）。
- **语言自动补全**：`MessageService`（`util/MessageService.java`）用户文件缺键时从 JAR 内置同语言取值并写回磁盘。

## config.yml 主要节点

- `config-version`
- 全局 `sounds`（request-received/countdown/teleport）、`particles.warmup`
- `modules`：tpa/back/dback/warp/home/pwarp，各含 `enabled`、`warmup`、`warmup-seconds`、`sounds`、`particles`；tpa 另有 `request-timeout-seconds`、`cooldown`、`allow-cross-world`、`disabled-worlds`，warp/home/pwarp 有 `default-amount/cost/permission`、`*-max-length` 等
- `modules.back.min-distance`（2026-09-12 新增，默认 16）：同世界传送时新位置与旧位置距离小于该值（格）则忽略本次 `/back` 记录；`0` 始终记录
- `modules.home.bed-home`（2026-09-15 新增，默认 true）与 `modules.home.bed-home-name`（默认 `重生点`）：玩家入睡并设置个人重生点（`PlayerBedEnterEvent` + `getBedEnterResult()==OK`，重复睡同一床按床方块比较跳过）时自动创建/覆盖该名称的家
- `language`、`proxy.enabled`、`server-id`
- `storage`（sqlite/mysql）

## lang/zh_CN.yml 主要键

- `prefix`、错误/状态消息（player-*/request-*/warp-*/home-*/back-*）
- `mode.*`（dialog/chat/sneak）、`list.*`（whitelist/blacklist）
- `network-reason.*`、`ui.*`（selector/settings/relation/request/request-list/button/chat/warp/home）
- `help.*`、`module-*`

## 踩过的坑

- `config-version` 升级逻辑只在低于当前版本时触发，手动改高版本号会跳过升级。
- 缺失 lang 键写回磁盘时可能因文件占用失败，需捕获异常。

## 当前待办

- `build.gradle.kts` 有 1 行未提交改动，确认是否为 config 相关（如 config-version 提升或新模块默认值）。
- 新增 `docs/perm/placeholders.md` 未提交，补充占位符清单。
