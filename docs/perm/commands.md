# 指令

## 玩家指令

| 指令 | 说明 |
| --- | --- |
| `/tpa [玩家]` | 请求传送到目标玩家；不填玩家时打开选择列表 |
| `/tpahere [玩家]` | 邀请目标玩家传送到自己；不填玩家时打开选择列表 |
| `/tpaccept` | 同意请求；存在多条时打开待处理列表 |
| `/tpdeny` | 拒绝请求；存在多条时打开待处理列表 |
| `/tpacancel` | 撤销自己发出的待处理请求 |
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

修改数据库类型或连接信息后，请重启服务器而不是只执行重载。
