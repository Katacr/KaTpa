# 数据模型与存储层（storage）

> 最后更新：2026-09-16（新增 JDBC 连接失效检测、自动重连与跨 Store 串行化）

## 现状

- 数据模型几乎全部为不可变 `record`，坐标类（`LocationRecord`/`Home`/`Warp`）内含 `server` 字段以支持跨服定位。
- 存储层采用**集中管理的单物理连接**模式：`SettingsStore` 持有 `JdbcConnectionManager`，其余 store 只提交完整 SQL 操作，不再保存裸 `Connection` 引用。

## 模型清单（file:line）

| 类 | 文件 | 核心字段 |
| --- | --- | --- |
| TeleportRequest | `model/TeleportRequest.java:8` | id, senderId, receiverId, type, createdAt, expiresAt, timeoutTask |
| AcceptMode | `model/AcceptMode.java:4` | 枚举 DIALOG/CHAT/SNEAK，`parse()` 支持中英别名 |
| ListType | `model/ListType.java:4` | 枚举 WHITELIST/BLACKLIST |
| KnownPlayer | `model/KnownPlayer.java:6` | uuid, name |
| NetworkPlayer | `model/NetworkPlayer.java:6` | id, name, server |
| LocationRecord | `model/LocationRecord.java:4` | server, world, x,y,z, yaw, pitch, timestamp |
| NetworkRequestData | `model/NetworkRequestData.java:6` | 跨服请求上下文（含 traveler/destination 解析） |
| RelationEntry | `model/RelationEntry.java:6` | targetId, targetName, type |
| Home | `model/Home.java:6` | ownerId, name, server, world, x,y,z, yaw, pitch, createdAt |
| Warp | `model/Warp.java:4` | name, server, world, x,y,z, yaw, pitch, permission, cooldownSeconds, cost, createdAt, updatedAt |
| RequestType | `model/RequestType.java:4` | 枚举 TPA/TPA_HERE |

## 存储层

- **双数据库**：SQLite（默认，`players.db`）/ MariaDB（`storage.type: mysql`）。
- **JdbcConnectionManager**（`storage/JdbcConnectionManager.java`）：唯一持有物理连接；公平 `ReentrantLock` 覆盖整个 JDBC callback，串行化全部 Store 和主线程查询；MySQL 每 30 秒至多执行一次 `Connection.isValid(2)`，失效时关闭旧连接并重建；SQLState `08xxx` 或 JDBC 连接异常会使当前连接失效，供下一次操作重连。执行中的失败操作不自动重放，避免离线收入累加等非幂等写入重复。
- **SettingsStore**（`storage/SettingsStore.java`）：创建连接管理器，建表 `players` + `relations`，全量载入内存缓存（modes/knownPlayers/relations），提供 `findKnownPlayer(name)`、`setRelation`（upsert，覆盖同 owner+target 的另一名单）。SQLite 的 PRAGMA 由连接初始化器设置，未来重建连接时也会重新应用。
- **BackStore / WarpStore / HomeStore**：共享连接管理器，各自单线程池异步写。
  - BackStore：back/dback 位置。
  - WarpStore：启动全量 `loadAll()`（`WarpStore.java`）；`reload()` 异步重读全表并在主线程合并。
  - HomeStore：**玩家级惰性加载**，进服按 ownerId `load()`（`HomeStore.java:89`），天然不存在跨服缓存陈旧问题。
- **跨服缓存一致性（2026-09-15）**：`WarpStore`/`PlayerWarpStore` 的 `loadAll()` 改为**非破坏性合并**（`warps.putAll(...)` + `keySet().retainAll(...)`，先补新再删旧），避免 GUI 读取到瞬时空列表；`readAll()` 在单线程池读全表到局部 map，`reload()` 再 `Bukkit.getScheduler().runTask` 到主线程 `applyLoaded()`，与主线程的 `save()` put 串行。写成功后在 `executeUpdate()` 内调用 `plugin.network().notifyDataChanged(topic)` 广播（见 network.md）。

## 踩过的坑

- 多 store 共享连接时，只有 `settings.close()` 关物理连接，其余 store 的 `close()` 只停线程池，否则会重复关闭连接导致异常。
- MySQL 下玩家进服需 `refreshPlayer()` 重新拉取（缓存可能在别的子服被改）。
- **预编译语句列/参数错位（2026-09-12）**：`WarpStore.save()` 与 `PlayerWarpStore.save()` 的 INSERT 列清单含 `server, world`，但绑定参数时只绑了 `server` 后直接跳到 `x`，**漏绑 `world`**，导致后续参数整体错位、最后一个占位符未设置，运行时报 `Parameter at position 18 is not set`（`/setwarp`、`/pwarp` 创建均失败）。已修复：分别补 `world` 绑定并顺移后续位。教训：SQL 列与 `setXxx(n, ...)` 必须逐位对照检查，勿用固定序号 grep 判断；复制粘贴的 INSERT 语句易传播同一缺陷。
- **长连接空闲失效（2026-09-16）**：Lobby 的同一连接 `conn=145459` 在玩家设置、back、home、warp、pwarp 等模块持续报 `Connection is closed`；MySQL 服务未重启，`wait_timeout=28800`，且数据库中已无 `katpa` 活跃连接。根因是 1.2.0 只在启动时 `DriverManager.getConnection()`，7 个 Store 永久保存同一裸连接，没有 `isValid()`、心跳或重连；不同 Store 的 executor 还会并发使用该连接，`BackStore` 的 `autoCommit=false` 事务可能吸收其他 Store 写入。1.2.1 改为统一 `JdbcConnectionManager`，完整操作持锁并自动换新连接；`BackStore.addDeathLocation` 同时补上异常 `rollback()` 与事务状态恢复。

## 当前待办

- 1.2.1 已部署到 Lobby：插件正常启用、MySQL 中存在新的 `katpa` 空闲连接，启动日志无连接异常。仍需在维护窗口手动 `KILL` KaTpa 连接或等待空闲超时，再确认日志出现一次“数据库连接已失效，正在重新连接”且下一次 back/home/warp/pwarp 操作恢复；本次未在生产服主动断开连接。
- 玩家加入和 pwarp GUI 仍有同步数据库查询，短时断网可能阻塞 Bukkit 主线程至 socket timeout；后续应移到数据库 executor，并批量化 GUI 的评分/收藏查询。
- `takePendingIncome` 当前仍是同步 SELECT + 异步 DELETE，跨服并发领取存在重复发放窗口；应单独设计领取状态机/幂等流水，不能靠盲目 SQL 重试解决。
