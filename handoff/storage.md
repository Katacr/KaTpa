# 数据模型与存储层（storage）

> 最后更新：2026-09-07

## 现状

- 数据模型几乎全部为不可变 `record`，坐标类（`LocationRecord`/`Home`/`Warp`）内含 `server` 字段以支持跨服定位。
- 存储层采用**单连接 + 多 store 共享**模式：`SettingsStore` 持有唯一物理 `Connection`，其余 store 复用。

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
- **SettingsStore**（`storage/SettingsStore.java`）：唯一物理连接，建表 `players` + `relations`，全量载入内存缓存（modes/knownPlayers/relations），提供 `findKnownPlayer(name)`、`setRelation`（upsert，覆盖同 owner+target 的另一名单）。
- **BackStore / WarpStore / HomeStore**：共享连接，各自单线程池异步写。
  - BackStore：back/dback 位置。
  - WarpStore：启动全量 `loadAll()`（`WarpStore.java:81`）。
  - HomeStore：**玩家级惰性加载**，进服按 ownerId `load()`（`HomeStore.java:80`）。

## 踩过的坑

- 多 store 共享连接时，只有 `settings.close()` 关物理连接，其余 store 的 `close()` 只停线程池，否则会重复关闭连接导致异常。
- MySQL 下玩家进服需 `refreshPlayer()` 重新拉取（缓存可能在别的子服被改）。

## 当前待办

- 无（存储层相对稳定，新模块若需持久化应评估是否走现有共享连接模式还是新 store）。
