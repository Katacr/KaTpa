# 配置与语言（config）

> 最后更新：2026-09-20（修复 ConfigUpdater 提取漏值）

## 现状

- 功能参数 `config.yml`（`src/main/resources/config.yml`），玩家消息/界面文本 `lang/zh_CN.yml`（通过 `config.language` 选择）。
- **配置自动升级**：`ConfigUpdater.checkAndUpdateConfig`（`util/ConfigUpdater.java`，`CURRENT_CONFIG_VERSION=5`）：备份旧文件 → 从 JAR 提取默认 → 回写用户值（跳过 `config-version` 与不存在的键）。
- **语言自动补全**：`MessageService`（`util/MessageService.java`）用户文件缺键时从 JAR 内置同语言取值并写回磁盘。

## config.yml 主要节点

- `config-version`
- 全局 `sounds`（request-received/countdown/teleport）、`particles.warmup`
- `modules`：tpa/back/dback/warp/home/pwarp，各含 `enabled`、`warmup`、`warmup-seconds`、`sounds`、`particles`；tpa 另有 `request-timeout-seconds`、`cooldown`、`allow-cross-world`、`disabled-worlds`，warp/home/pwarp 有 `default-amount/cost/permission`、`*-max-length` 等；`modules.pwarp` 另有 `total-slots`、`leaderboard-cache-size`、`create-cost.{currency,money,points}`、`cooldown-seconds`（全局按玩家传送冷却，默认 30）
- `modules.back.min-distance`（2026-09-12 新增，默认 16）：同世界传送时新位置与旧位置距离小于该值（格）则忽略本次 `/back` 记录；`0` 始终记录
- `modules.home.bed-home`（2026-09-15 新增，默认 true）与 `modules.home.bed-home-name`（默认 `重生点`）：玩家**右键床**（`PlayerInteractEvent`，白天夜晚均可）时自动创建/覆盖该名称的家；重复右键同一张床按内存 bedKey 跳过
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
- **配置升级丢失用户值（2026-09-15，严重）**：`config-version` 1→2→3 升级时日志只显示「已保留 2 项」，用户全部自定义值（proxy.enabled、storage.type=MySQL、mysql 账号密码、server-id 等）被 JAR 默认值覆盖。根因在 `ConfigUpdater.collectValues`：递归取子节值时误用 `section.get(path)`（含前缀的绝对路径），而 `ConfigurationSection.get()` 要求**相对该 section 的相对路径**；MemorySection 用绝对路径取值返回 null，导致所有嵌套 section（modules/sounds/particles/proxy/storage）的叶子提取失败，只提取到 3 个顶层叶子（config-version/language/server-id）。修复：改为 `section.get(key)`（相对路径）。对比 KaMenu `ConfigUpdater.kt` 用 `getKeys(true)` 一次性取全量路径，天然规避此坑。**教训：`ConfigurationSection.get/set` 的 path 语义是「相对当前 section」；递归遍历必须用相对键。**
- 事故处置：受影响服务器（全部 7 台）从各自 `plugins/KaTpa/config_v2_backup_*.yml` 恢复 `config.yml`（改回 `config-version: 2`），再用修复版 jar 重启重新升级；升级后「已保留 80 项」，MySQL/密码/proxy/server-id 均正确。

## 当前待办

- 其余 6 台后端已恢复配置并升级到 1.2.1（2026-09-20），确认各服 `storage.type=MySQL` 与账号密码正确。
- 配置版本已升至 v5（新增 `modules.pwarp.create-cost.*` 与 `modules.pwarp.leaderboard-cache-size`）；Lobby 已升级，其余 6 台待部署新 jar 后自动升级。
