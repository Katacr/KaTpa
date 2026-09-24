# 权限

| 权限 | 默认拥有者 | 说明 |
| --- | --- | --- |
| `katpa.use` | 所有玩家 | 使用传送请求、接受、拒绝和撤销指令 |
| `katpa.setting` | 所有玩家 | 打开并修改个人设置和名单 |
| `katpa.admin` | OP | 使用 `/katap reload` |
| `katpa.cooldown.bypass` | OP | 不受请求冷却限制 |
| `katpa.back` | 所有玩家 | 使用 `/back` 返回上次位置 |
| `katpa.dback` | 所有玩家 | 使用 `/dback` 返回死亡位置 |
| `katpa.warp` | 所有玩家 | 使用 `/warp` 传送到地标 |
| `katpa.warp.admin` | OP | 使用 `/setwarp`、`/delwarp` 和 `/katap warp ...` 管理地标（编辑、创建、删除、重命名、图标、设置字段） |
| `katpa.home` | 所有玩家 | 使用 `/home`、`/sethome` 和 `/delhome` 管理个人家 |
| `katpa.home.amount.<n>` | — | 允许设置 n 个家；默认 1，取玩家持有的最大值 |
| `katpa.pwarp.use` | 所有玩家 | 使用 `/pwarp`、`/pw` 浏览与传送到玩家地标 |
| `katpa.pwarp.create` | 所有玩家 | 创建玩家地标 |
| `katpa.pwarp.admin` | OP | 编辑或删除任意玩家地标（用于清理违规地标） |
| `katpa.pwarp.amount.<n>` | — | 允许创建 n 个玩家地标；默认 1，取玩家持有的最大值 |


`katpa.home.amount.*` 同理，授予例如 `katpa.home.amount.5` 即可让玩家设置 5 个家。

`katpa.pwarp.amount.*` 同理，授予例如 `katpa.pwarp.amount.3` 即可让玩家创建 3 个玩家地标。

如果使用权限插件，可以根据服务器需要移除默认权限或只授予特定玩家组。
