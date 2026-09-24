# 服务层与命令层（services）

> 最后更新：2026-09-20（右键床自动设家；家编辑器；ConfigUpdater 提取修复）

## 现状

- 业务服务集中在 `service/`，命令在 `command/`，事件监听在 `listener/PlayerListener.java`。
- 模块开关（`modules.<module>.enabled`）控制服务创建与命令注册。

## 服务清单（file:line）

| Service | 职责 | 关键方法 |
| --- | --- | --- |
| RequestService | 请求生命周期（创建/同意/拒绝/撤销/超时/跨服）、双击潜行状态机 | `create(:40)` `accept(:172)` `cancel(:247)` `handleSneak(:472)` `cancelForPlayer(:516)` |
| TeleportService | 吟唱倒计时、移动/伤害中断、异步传送、跨服落点 | `begin(:63)` `beginDirect(:162)` `handleMove(:205)` `handleDamage(:220)` `finish(:242)` |
| CooldownTracker | 请求冷却起点 + 向上取整剩余秒数 | `start(:12)` `remainingSeconds(:17)` |
| SoundService | 可配置音效（request/countdown/teleport）按模块过滤 | `play(:26)` `playAt(:32)` |
| ParticleService | 吟唱粒子（默认 PORTAL） | `spawnWarmup(:21)` |
| BackService | 记录并 `/back` 同服/跨服返回 | `recordLocation` `back(:48)` `handleArrival(:71)` |
| DbackService | 死亡位置记录（权限槽位滚动）、`/dback [序号]` | `recordDeath(:37)` `dback(:54)` `maxSlots(:22)` |
| WarpService | 地标传送（权限/冷却/Vault 付费/跨服）、管理接口 | `warp(:24)` `setWarp(:56)` `payCost(:207)` |
| HomeService | 玩家私家（数量上限按权限）、跨服 | `home(:37)` `setHome(:60)` `maxHomes(:22)` |
| PlayerWarpService | 玩家地标传送/创建/编辑/评分/收藏；**创建收费（Vault/PlayerPoints，2026-09-21）**、**收费二次确认**、**全局按玩家冷却**、`updateLocation`、`reloadData()` | `warp(player,name,confirmed)`（`confirmed=false` 且收费时发 `pwarp-cost-confirm` 可点击消息；冷却读 `modules.pwarp.cooldown-seconds`）、`create`→`chargeCreateCost`、`rate`（触发排行榜缓存重算）、`updateLocation`、`reloadData` |
| PlayerListener | 玩家事件（加入/退出/移动/受伤/潜行/传送）+ **右键床自动设家（2026-09-15）** | `onBedInteract`（`PlayerInteractEvent` RIGHT_CLICK_BLOCK + `_BED` + 主手）→ `home().setBedHome(player, modules.home.bed-home-name, bed)`；配置 `modules.home.bed-home`(默认 true)/`bed-home-name`(默认 重生点)；存在则覆盖、满员提示走 `setHome` 既有逻辑 |

## 请求生命周期要点

- **UUID 体系**：每条请求随机 `requestId`；三索引 `outgoing`(sender→req)、`incoming`(receiver→reqId→req)、`byId`。`outgoing` 保证发送者同时只有一条请求。
- **超时**：本地 `runTaskLater`（默认 `request-timeout-seconds`=30）；跨服额外 `scheduleNetworkFallback` 兜底防丢包。
- **撤销**：`cancel(sender, requestId)` 校验 requestId 防旧按钮误撤，不返还冷却。
- **特殊流**：白名单自动同意（`startAccepted`）、黑名单静默拦截、双击潜行（SneakState 状态机）。

## 传送（吟唱）逻辑

- `begin/beginDirect/beginNetwork` 创建 `WarmupSession`，5 tick 间隔运行；每 1s 播放 countdown 音效 + warmup ActionBar + 粒子。
- **中断**：`handleMove` 真实位移（非转视角）、`handleDamage` 未取消伤害、离线/被取消均中断。
- `finish` 最终异步传送，回主线程通知双方；跨服用 `arriveNetwork` 在目标服落点。
- **吟唱前目标校验（2026-09-12 新增）**：`TeleportService.ensureTargetAvailable(traveler, targetServer, targetWorld, worldMessageKey, worldArgs)` —— 目标在其它子服时要求 `network().isServerAvailable(targetServer)`（并 `available()`），同服时要求 `Bukkit.getWorld(targetWorld) != null`；失败发提示并返回 false，调用方据此不启动吟唱。
- **跨服 TPA 提前拒绝（2026-09-21）**：跨服 TPA 源服无法预知目的地世界，原先只在**落点阶段**（`TeleportService.arriveNetwork` → `canUse`）校验，导致旅行者已被代理切到目标服才失败、滞留在目标服。现于**接收者所在子服的接受环节**提前校验：`RequestService.rejectIfReceiverWorldDisabled(receiver, request)`（接收者当前世界命中 `modules.tpa.disabled-worlds` 时）——本地请求直接拒绝并通知发送者 `target-world-disabled`，跨服请求发 `network.deny` 并 `remove`；`accept()` 与 `receiveNetwork()` 的白名单自动接受分支均已接入。`/tpa` 目的地为接收者、`/tpahere` 旅行者为接收者，两者皆覆盖。落点阶段校验保留作兜底。
- **世界黑名单（2026-09-21）**：新增重载 `ensureTargetAvailable(module, traveler, targetServer, displayServer, targetWorld, ...)`，模块名非空时先校验 `modules.<module>.disabled-worlds`（`isWorldDisabled`/`isCurrentWorldDisabled`），命中则提示 `target-world-disabled` 并拒绝。`back`/`dback`/`home`/`warp`/`pwarp` 全部接入；各模块创建/更新点位（`HomeService.setHome`/`updateLocation`、`WarpService.setWarp`、`PlayerWarpService.create`/`updateLocation`）在玩家当前世界命中黑名单时拒绝并提示 `world-creation-disabled`；`BackService.recordLocation` 与 `DbackService.recordDeath` 在黑名单世界直接跳过记录。`tpa` 沿用原有 `disabled-worlds`（`canUse`/`canUseNetworkEndpoint`）。
  - 接入点：`back`/`dback`/`home`/`warp`/`pwarp` 的本服与跨服分支（共 10 处），以及 `beginNetwork`（tpa 跨服，用 `NetworkRequestData.destinationServer()`）。
  - 新增语言键：`target-server-unavailable`、`target-world-unloaded`；本服世界未加载仍走各模块原有的 `*-world-unloaded` 键（含名称）。
  - 跨服目标世界是否加载无法在源服判断，仍由目标服落点阶段（`handleArrival`/`arriveNetwork`）兜底校验。

## 命令与权限

| 命令 | 功能 | 权限 |
| --- | --- | --- |
| `/katap help\|reload` | 帮助/重载 | `katpa.admin`（reload） |
| `/tpa` `/tpahere` | 请求/邀请传送 | `katpa.use` |
| `/tpaccept` `/tpdeny` `/tpacancel` | 接受/拒绝/撤销（兼容别名 tpaaccept/tpadeny） | `katpa.use` |
| `/tpasetting` | 接受模式/黑白名单/重载 | `katpa.setting` |
| `/back` `/dback [n]` | 返回上次/死亡位置 | `katpa.back` / `katpa.dback` |
| `/warp` `/setwarp` `/delwarp` | 地标传送/创建/删除 | `katpa.warp` / `katpa.warp.admin` |
| `/pw <名称>` | 玩家地标快捷传送（收费地标先弹二次确认） | `katpa.pwarp.use` |
| `/pwarp` | 打开玩家地标排行榜 | `katpa.pwarp.use` |
| `/pwarp leaderboard\|favorites\|mine\|history` | 排行榜/收藏/我的地标/历史 | `katpa.pwarp.use` |
| `/pwarp admin edit\|delete\|reload` | 编辑器/强行删除/重载数据+排行榜缓存 | `katpa.pwarp.admin` |
| `/katap pwarp ...` | 玩家地标创建/编辑/评分/收藏等（`set` 仅 `name`/`cost`/`desc`） | `katpa.pwarp.use` 等 |
| `/home` `/sethome` `/delhome` | 家传送/创建/删除 | `katpa.home` |

数量上限按权限 `katpa.home.amount.<n>`、`katpa.pwarp.amount.<n>` 动态计算（`dback` 已改为单条，不再有 `katpa.dback.amount.<n>`）。

## 玩家地标创建收费与收费确认（2026-09-21）

- **可选前置 PlayerPoints**：`util/PointsHook.java` 反射挂载（`PlayerPoints.getInstance().getAPI()`），未安装时 `available()` 为 false、各方法安全降级；`KaTpaPlugin.points()`；`plugin.yml` softdepend 增 `PlayerPoints`。
- **创建收费**：`config.yml: modules.pwarp.create-cost.{currency,money,points}`；`currency=money` 用 Vault、`points` 用 PlayerPoints（二选一，金额 0 或前置缺失即免费）。仅在**新建**时经 `PlayerWarpService.chargeCreateCost` 扣除（编辑不收费），失败发 `pwarp-create-insufficient-funds`/`pwarp-create-insufficient-points`。
- **收费二次确认**：`PlayerWarpService.warp(player, name, confirmed=false)`，非创建者且 `cost>0` 时发 `pwarp-cost-confirm` 可点击消息（`ClickableText.sendClickableMulti`，`[确定]` 回调 `warp(..., true)`、`[取消]` 发 `pwarp-cost-cancelled`，30s 失效），不再直接扣费。菜单 `katpa: pwarp warp` 与 `/pw` 均走此路径。
- **排行榜缓存**：`WarpRatingStore` 维护前 `leaderboard-cache-size`（默认 30）名缓存（`LeaderboardEntry`=ID+平均星级+加权得分），`leaderboard(0)` 读全量缓存、`cachedEntry(id)` O(1) 查询；`computeLeaderboard` 按**权重分**排序。触发：`rate()` 后、创建/删除经 `PlayerWarpStore.markLeaderboardDirty()` 在写库成功后、`/pwarp admin reload`、跨服 `warp_rating` 主题广播（`refreshLeaderboard(false)` 防回环）。

## PlayerListener 监听事件

- Join：记忆玩家名、跨服上线、back 待确认检查、刷新存储。
- Quit：记录 back 位置、取消请求与吟唱。
- Move：真实位移中断吟唱。
- Teleport：记录 back 起点，本插件 PLUGIN 传送用 `isOwnTeleport` 跳过。
- Death：按槽位记录死亡位置。
- Damage：有效伤害中断吟唱。
- ToggleSneak：双击潜行状态机。
- BedInteract（2026-09-15）：玩家**右键床**（`PlayerInteractEvent` RIGHT_CLICK_BLOCK + `_BED` + 主手）时自动 `home().setBedHome`，白天夜晚均可。去重：`HomeService.lastBedHomes`（内存 Map<UUID, bedKey>）以「床头格世界:xyz」为整张床 key（`Bed.getPart()/getFacing()` 归一头/脚两格），同一张床重复右键跳过，换床才更新。曾短暂尝试 `PlayerBedEnterEvent`（入睡事件），因白天无法设点已弃用；`isSpawnSet()` 在 1.16.5/1.21.x API 均不存在。非 setworldspawn 全局出生点事件。

## 家编辑器（2026-09-15）

- `home_selector.yml` / `home_manager.yml` 的列表右键由「删除」改为 `katpa: home edit <name>` 打开新菜单 `home_editor.yml`。
- `home_editor.yml` 提供三个按钮：图标（`home icon <name>`，手持物品设置）、更新位置（`home update <name>` → `HomeService.updateLocation`）、删除（`home delete <name>`）。
- 新增 `InteractionPlatform.showHomeEditor(Player, Home)` + `InteractionService` 委托 + `InventoryInteractionPlatform` 实现（注入 home_* 变量）。
- `KaTpaGuiActions.reopenHomeMenu`：图标/更新位置后按来源菜单回编辑器或管理列表。
- `HomeService.updateLocation(Player, String)`：仅当家存在时以玩家当前位置覆盖坐标/世界/服务器，保留描述与图标。

## 踩过的坑

- 吟唱期间移动判定必须用坐标差而非事件，转视角（`look` 不变坐标）不应中断。
- 本插件触发的传送需打 `isOwnTeleport` 标记，避免被 PlayerTeleportEvent 二次记录为 back 起点（见 `fa81194` 提交）。
- 跨服传送：源服完成吟唱后切服，目标服读最新位置落点；旧 UUID / 移动 / 受伤 / 离线 / 超时均安全中断。

## 当前待办

- ~~进行中重构（未提交）：back/dback/home/warp 的跨服传送改为**先 `beginDirect` 吟唱再请求切服**~~ —— **2026-09-12 已完成**：`BackService`/`DbackService` 原有实现基础上，补齐 `HomeService.teleportCrossServer`、`WarpService.teleportCrossServer`、`PlayerWarpService.teleportCrossServer`，三者均在 `ensureTargetAvailable` 校验通过后调用 `beginDirect(module, () -> backRequest(...))`，做到本服/跨服吟唱一致（`modules.<module>.warmup-seconds`，默认 3s）。
- 新增 `placeholder/` 模块（KaTpaPlaceholderExpansion）未提交，需补充占位符清单与文档到 services.md。
