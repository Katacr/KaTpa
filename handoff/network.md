# 跨服网络层（network）

> 最后更新：2026-09-15（新增 `core/data_changed` 缓存同步广播）

## 现状

- 通过 Bukkit 插件消息通道 `kaproxy:main` 与 **KaProxy** 代理端通讯，实现全服在线玩家发现与跨服传送。
- `KaProxyProtocol`（`network/KaProxyProtocol.java`）定义二进制、版本化协议（MAGIC=0x4B415058, VERSION=1, MAX_PACKET_BYTES=1MB）。
- `CrossServerService`（`network/CrossServerService.java`）实现 `PluginMessageListener`，`onPluginMessageReceived` 按 module/action 路由到 RequestService/BackService。

## 协议要点

- **在线玩家发现**：后端每 30s 发 `sync_request` 心跳拉取 presence 快照，更新 `onlinePlayers` 与 `realServerId`；`available()` 要求 90s 内收到过 presence。
- **在线子服列表（2026-09-12 新增）**：presence 包在玩家列表后追加 `int serverCount + serverName(UTF)*`，由 KaProxy 侧 ping 探活后仅下发在线子服。后端 `CrossServerService` 解析到 `servers` 集合，`isServerAvailable(name)` 供吟唱前校验；旧版代理无该字段时 `readServerList` 捕获 `EOFException` 返回 null，退化为“仅要求代理连通”。
- **tpa 事务**：`request_create/accept/deny/cancel/warmup_*/arrival_*`。
- **back 事务**：`back_request/back_arrival_complete/failed`。
- **数据变更同步（2026-09-15 新增）**：后端写入 warp/player_warp 成功后发 `core/data_changed`（payload：`topic` UTF，白名单 warp/player_warp/**warp_rating**），KaProxy `KaProxyCore.handleDataChanged` 转发给除来源外的所有后端（payload：`sourceServer` UTF + `topic` UTF）；后端收到后 `WarpStore.reload()` / `PlayerWarpStore.reload()`（并触发排行榜缓存重算）/ `WarpRatingStore.refreshLeaderboard(false)`。`CrossServerService.notifyDataChanged(topic)` 负责在写成功后从 DB 线程调度到主线程发送（需 `available()`）。玩家加入时 `refreshGlobalCaches()`（2s 去抖）兜底重载，覆盖该服离线期间错过的广播。
- **`warp_rating` 主题（2026-09-21）**：评分成功或创建/删除地标后广播，其他后端仅重算排行榜缓存（`refreshLeaderboard(false)`，不再回环广播）；KaProxy `DATA_SYNC_TOPICS` 已加入该主题（`KaProxyCore.java:44`）。
- 未连接代理（`!available()`）且非 sync 的消息直接返回 false。

## 跨服传送流程

1. 源服完成吟唱（见 services.md 进行中重构）。
2. 请求 KaProxy 切服，携带目标服最新位置。
3. 目标服 `arriveNetwork` 读取目标玩家最新位置完成传送。
4. 移动/受伤/离线/超时/旧 UUID 操作均安全中断。

## 踩过的坑

- 跨服请求必须 `scheduleNetworkFallback` 兜底，防止代理丢包导致请求永久挂起。
- 所有子服必须安装相同版本 KaTpa，代理端启用 `modules.tpa`。
- 跨服网络建议 `storage.type: mysql` 共享，所有子服同一组 mysql 参数。

## 当前待办

- 确认 `server-id` 与 `proxy.enabled` 配置在跨服部署中的实际作用（README 提到 "KaProxy 服务器标识"）。
- 网络相关配置变更需重启插件（`storage.type` 同理），普通配置可 `/katap reload`。
