# 权限

| 权限 | 默认拥有者 | 说明 |
| --- | --- | --- |
| `katpa.use` | 所有玩家 | 使用传送请求、接受、拒绝和撤销指令 |
| `katpa.setting` | 所有玩家 | 打开并修改个人设置和名单 |
| `katpa.admin` | OP | 使用 `/katap reload` |
| `katpa.cooldown.bypass` | OP | 不受请求冷却限制 |
| `katpa.back` | 所有玩家 | 使用 `/back` 返回上次位置 |
| `katpa.dback` | 所有玩家 | 使用 `/dback` 返回死亡位置 |
| `katpa.dback.amount.<n>` | — | 允许保存 n 个死亡位置；默认 1，取玩家持有的最大值 |
| `katpa.warp` | 所有玩家 | 使用 `/warp` 传送到地标 |
| `katpa.warp.admin` | OP | 使用 `/setwarp` 和 `/delwarp` 管理地标 |
| `katpa.home` | 所有玩家 | 使用 `/home`、`/sethome` 和 `/delhome` 管理个人家 |
| `katpa.home.amount.<n>` | — | 允许设置 n 个家；默认 1，取玩家持有的最大值 |

`katpa.dback.amount.*` 是动态权限，不写在 `plugin.yml` 中。在权限插件中授予例如 `katpa.dback.amount.3` 即可让玩家保存 3 个死亡位置。玩家同时持有多个该权限时取最大值。

`katpa.home.amount.*` 同理，授予例如 `katpa.home.amount.5` 即可让玩家设置 5 个家。

如果使用权限插件，可以根据服务器需要移除默认权限或只授予特定玩家组。
