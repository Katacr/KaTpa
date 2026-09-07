# 配置与语言（config）

> 最后更新：2026-09-07

## 现状

- 功能参数 `config.yml`（`src/main/resources/config.yml`），玩家消息/界面文本 `lang/zh_CN.yml`（通过 `config.language` 选择）。
- **配置自动升级**：`ConfigUpdater.checkAndUpdateConfig`（`util/ConfigUpdater.java`，`CURRENT_CONFIG_VERSION=1`）：备份旧文件 → 从 JAR 提取默认 → 回写用户值（跳过 `config-version` 与不存在的键）。
- **语言自动补全**：`MessageService`（`util/MessageService.java`）用户文件缺键时从 JAR 内置同语言取值并写回磁盘。

## config.yml 主要节点

- `config-version`
- 全局 `sounds`（request-received/countdown/teleport）、`particles.warmup`
- `modules`：tpa/back/dback/warp/home，各含 `enabled`、`warmup-seconds`、`request-timeout-seconds`、`cooldown`、`disabled-worlds`、`default-amount/cost/permission` 等
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
