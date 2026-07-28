# 跨服传送

KaTpa 可以配合 KaProxy，让玩家向其他子服务器的玩家发送请求。请求被接受后，需要传送的玩家先完成倒计时，然后自动切换服务器并到达目标玩家身边。

## 准备工作

* Velocity 3.4 或兼容版本，或 BungeeCord 1.21
* 每个子服安装相同版本的 KaTpa
* 代理端安装 KaProxy
* 所有子服使用同一个 MySQL 或 MariaDB 数据库保存 KaTpa 设置

## 代理端设置

1. 将 `KaProxy-1.0.0.jar` 放入代理服务器的 `plugins` 文件夹。
2. 启动代理服务器，让 KaProxy 生成配置。
3. 确认 KaProxy 配置中 Tpa 模块已开启：

```yaml
modules:
  tpa:
    enabled: true
```

4. 重启代理服务器，或执行 `/kaproxy reload`。

## 子服设置

每个子服的 `plugins/KaTpa/config.yml` 都需要启用代理功能：

```yaml
proxy:
  enabled: true

storage:
  type: mysql
  mysql:
    host: 127.0.0.1
    port: 3306
    database: katpa
    username: katpa
    password: change-me
    use-ssl: false
```

请将示例中的数据库地址、账号和密码替换为自己的信息，并确保所有子服填写相同的数据库配置。完成后重启所有子服。

## 玩家体验

启用后，`/tpa` 和 `/tpahere` 的玩家列表会显示整个群组服的在线玩家。白名单、黑名单、接收方式、冷却和传送中断规则在跨服请求中同样有效。

默认情况下，如果目标玩家在倒计时期间切换到另一个子服，KaProxy 会继续前往目标玩家当前所在的服务器。

## 排查

如果提示“跨服服务当前不可用”，请依次检查：

1. KaProxy 是否已在代理端正常启用。
2. KaProxy 的 Tpa 模块是否开启。
3. 当前子服的 `proxy.enabled` 是否为 `true`。
4. 玩家是否已经正常通过代理进入子服。
5. 代理端和子服控制台是否出现报错。
