# 指令

## 玩家指令

| 指令 | 说明 |
| --- | --- |
| `/tpa [玩家]` | 请求传送到目标玩家；不填玩家时打开选择列表 |
| `/tpahere [玩家]` | 邀请目标玩家传送到自己；不填玩家时打开选择列表 |
| `/tpaccept` | 同意请求；存在多条时打开待处理列表 |
| `/tpdeny` | 拒绝请求；存在多条时打开待处理列表 |
| `/tpacancel` | 撤销自己发出的待处理请求 |
| `/back` | 返回上次位置 |
| `/dback` | 返回最近一次死亡位置 |
| `/warp [名称]` | 传送到地标；不填名称时打开选择列表（仅传送，无二级管理指令） |
| `/home [名称]` | 传送到个人家；不填名称时打开选择列表 |
| `/tpasetting` | 打开个人设置 |
| `/tpasetting mode <dialog\|chat\|sneak>` | 修改请求接收方式 |
| `/tpasetting <whitelist\|blacklist>` | 打开指定名单管理界面 |
| `/tpasetting <whitelist\|blacklist> <add\|remove> <玩家>` | 添加或移除名单成员 |
| `/katap help` | 查看游戏内帮助 |

兼容别名：`/tpaaccept`、`/tpadeny`、`/tpasettings`。

## 管理员指令

| 指令 | 说明 | 权限 |
| --- | --- | --- |
| `/katap reload` | 重载功能配置、语言和代理开关 | `katpa.admin` |
| `/katap warp edit <名称>` | 打开地标编辑器 GUI（描述、图标、权限、冷却、费用、重命名） | `katpa.warp.admin` |
| `/katap warp create <名称>` | 在当前位置创建地标 | `katpa.warp.admin` |
| `/katap warp delete <名称>` | 删除地标 | `katpa.warp.admin` |
| `/katap warp rename <旧名称> <新名称>` | 重命名地标 | `katpa.warp.admin` |
| `/katap warp icon <名称>` | 用玩家手持物品设置地标图标 | `katpa.warp.admin` |
| `/katap warp set <字段> <名称> [值]` | 修改地标字段（`name`/`permission`/`cooldown`/`cost`/`desc`），`desc` 后接多词描述，`permission` 留空即清除 | `katpa.warp.admin` |
| `/setwarp [名称]` | 在当前位置创建或更新地标（命令式快捷创建） | `katpa.warp.admin` |
| `/delwarp [名称]` | 删除地标 | `katpa.warp.admin` |
| `/sethome [名称]` | 创建或管理个人家 | `katpa.home` |
| `/delhome [名称]` | 删除个人家 | `katpa.home` |

## 玩家地标指令

玩家可以创建属于自己的公共地标，其他玩家可浏览、传送并评分，创建者可获得传送收入。

| 指令 | 说明 |
| --- | --- |
| `/pw <名称>` | 快捷传送到指定玩家地标（收费地标先弹二次确认） |
| `/pwarp` | 打开玩家地标排行榜 |
| `/pwarp leaderboard` | 打开排行榜（别名 `top`/`rank`） |
| `/pwarp favorites` | 打开我的收藏（别名 `favorite`/`fav`） |
| `/pwarp mine` | 打开我的地标管理列表（别名 `my`/`manager`） |
| `/pwarp history` | 打开历史传送列表（别名 `his`） |
| `/pwarp admin edit <名称>` | 打开任意地标的编辑器（管理员） |
| `/pwarp admin delete <名称>` | 强行删除任意地标（管理员） |
| `/pwarp admin reload` | 仅重载地标数据并重算排行榜缓存（管理员） |
| `/katap pwarp edit <名称>` | 打开自己的地标编辑器（描述、图标、费用、更新位置、重命名） |
| `/katap pwarp create <名称>` | 在当前位置创建玩家地标（可配置消耗金币/点券） |
| `/katap pwarp delete <名称>` | 删除自己的地标（管理员可删除任意地标） |
| `/katap pwarp rename <旧名称> <新名称>` | 重命名自己的地标 |
| `/katap pwarp icon <名称>` | 用玩家手持物品设置地标图标 |
| `/katap pwarp rate <名称> <1-5>` | 为地标评分（1-5 星） |
| `/katap pwarp set <字段> <名称> [值]` | 修改字段（`name`/`cost`/`desc`），`desc` 后接多词描述 |

> 创建数量上限由权限 `katpa.pwarp.amount.<n>` 控制（默认 1）。`katpa.pwarp.admin` 可编辑或删除任意玩家地标（用于清理违规地标）。
>
> 玩家地标列表条目统一为**左键传送、右键评分、Q 键收藏/取消**；「我的地标」管理列表条目为**左键/右键编辑**。

> `/warp` 仅用于传送，所有地标编辑操作请使用 `/katap warp ...`。GUI 内右键地标也会打开编辑器（仅 `katpa.warp.admin` 可见）。

修改数据库类型或连接信息后，请重启服务器而不是只执行重载。
