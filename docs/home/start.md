# 开始使用

## 安装

1. 确认服务器正在使用 Java 21。
2. 将 `KaTpa-1.0.0.jar` 放入服务器的 `plugins` 文件夹。
3. 启动或重启服务器。
4. 首次启动会生成 `plugins/KaTpa/config.yml`、`plugins/KaTpa/lang/` 和玩家数据文件。
5. 在控制台看到 KaTpa 已启用后，即可进入游戏使用。

首次启动可能需要下载运行所需文件，因此耗时会比之后启动稍长。

## 第一次发送请求

输入：

```text
/tpa
```

在弹出的玩家列表中选择目标。也可以直接输入玩家名：

```text
/tpa Steve
```

对方同意后，传送玩家会进入倒计时。倒计时期间移动或受到伤害会中断传送。

## 检查指令

输入 `/katap help` 可以查看游戏内帮助。如果普通玩家无法使用，请让管理员检查 `katpa.use` 权限。

## 修改配置

功能设置位于 `plugins/KaTpa/config.yml`，显示文本位于 `plugins/KaTpa/lang/`。大部分修改可通过以下指令生效：

```text
/katap reload
```

修改数据库类型或数据库连接信息后需要重启服务器。
