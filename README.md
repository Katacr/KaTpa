# KaTpa

KaTpa 是面向 Paper 1.21.7 与 Spigot 1.21.6+、JDK 21 的玩家传送请求插件。它使用服务器原生 Dialog（Paper 原生 Dialog 或 Spigot Bungee Dialog）、可点击文本和 SQLite 持久化，不依赖其他服务器插件。单个 JAR 会在启动时自动探测当前核心并选择对应的 Dialog 实现，启动日志会打印所用平台（Paper / Spigot）。

## 指令

| 指令 | 用途 |
| --- | --- |
| `/katap help` | 查看完整指令帮助 |
| `/katap reload` | 重载配置和语言文件，需要管理员权限 |
| `/tpa [玩家]` | 请求传送到目标玩家；未输入玩家时打开在线玩家列表对话框 |
| `/tpahere [玩家]` | 邀请目标玩家传送到自己；未输入玩家时打开在线玩家列表对话框 |
| `/tpaccept` | 同意当前请求，兼容 `/tpaaccept` |
| `/tpdeny` | 拒绝当前请求，兼容 `/tpadeny` |
| `/tpasetting` | 打开个人设置对话框 |
| `/tpasetting mode <dialog\|chat\|sneak>` | 切换请求接受方式 |
| `/tpasetting <whitelist\|blacklist>` | 打开名单管理对话框 |
| `/tpasetting <whitelist\|blacklist> <add\|remove> <玩家>` | 管理在线或进入过服务器的玩家 |
| `/tpasetting reload` | 重载功能配置和语言文件 |

普通权限为 `katpa.use`、`katpa.setting`；管理员重载权限为 `katpa.admin`，`katpa.cooldown.bypass` 可绕过请求冷却。

## 行为

- 每名发送者同一时间只能发出一条待处理请求，但同一接收者可同时接收多名玩家的请求。
- 每条请求携带独立 UUID 上下文；请求列表仅在新增、处理或真正超时时直接推送刷新，并提供可点击文本及单列原生同意按钮。
- 待处理列表不会定时刷新；最后一条请求被同意、拒绝或超时后会自动关闭 Dialog，不显示空列表页面。
- 接收方式可在设置对话框中单选确认 Dialog、聊天可点击文本或超时前双击潜行。
- 接收者白名单内的请求自动同意，黑名单内的请求自动拒绝并通知双方。
- 主设置界面只显示白名单和黑名单人数；名单管理页逐行展示成员并支持 `[移除]`，添加成员使用独立的在线玩家选择 Dialog。
- 请求同意后进入带倒计时音效和末影粒子的可配置传送时间；玩家改变坐标或受到有效伤害会中断传送。
- 准备传送、倒计时、传送完成和传送中断状态均通过 ActionBar 显示，减少聊天栏提示刷屏。
- 冷却从一次有效请求发出时开始；目标不存在、玩家忙碌或世界受限不会消耗冷却。
- 接受模式、最后玩家名、白名单和黑名单保存在 `plugins/KaTpa/players.db`。

## 配置

功能参数位于 `plugins/KaTpa/config.yml`，包括请求超时、吟唱时间、双击潜行间隔、请求冷却、跨世界开关、禁用世界，三类交互音效及吟唱粒子。全部玩家消息和界面文本位于 `plugins/KaTpa/lang/zh_CN.yml`；`config.yml` 的 `language` 节点选择语言文件。

## 构建

```bash
cd /home/plugins/KaTpa
./gradlew clean build
```

可部署文件生成在 `build/libs/KaTpa-1.0.0.jar`。构建会同时编译 `src/main`（Paper 适配器）与 `src/spigot`（Spigot 适配器）两个 sourceSet，并由 shadowJar 打进同一 JAR。插件仅内置轻量的 Libby；首次开服时会自动从 Maven Central 或阿里云镜像下载 SQLite JDBC 到服务器的 `libraries` 目录。本地 Paper 1.21.7 测试服可通过 `./gradlew runServer` 启动。
