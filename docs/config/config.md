# 服务器配置

KaTpa 的功能设置位于 `plugins/KaTpa/config.yml`。

## 常用设置

| 节点 | 默认值 | 用途 |
| --- | --- | --- |
| `request-timeout-seconds` | `30` | 请求等待多少秒后自动过期 |
| `language` | `zh_CN` | 使用 `lang` 文件夹中的语言文件 |
| `server-id` | `local` | 子服标识，跨服模式下用于记录玩家所在服务器 |
| `warmup-seconds` | `3` | 接受请求后的传送准备时间 |
| `double-sneak-interval-seconds` | `2` | 双击潜行两次按键的最大间隔 |
| `cooldown.enabled` | `true` | 是否启用请求冷却 |
| `cooldown.seconds` | `30` | 两次有效请求之间需要等待的秒数 |
| `allow-cross-world` | `true` | 是否允许跨世界传送 |
| `disabled-worlds` | `[]` | 禁止使用 KaTpa 的世界名称 |

禁用世界示例：

```yaml
disabled-worlds:
  - resource_world
  - event_world
```

世界名称区分大小写。

## 音效

三类音效可以分别启用、关闭或替换：

* `sounds.request-received`：收到请求时的提示音
* `sounds.countdown`：传送倒计时音效
* `sounds.teleport`：完成传送时的音效

每组音效都可以设置 `enabled`、`sound`、`volume` 和 `pitch`。

## 粒子

`particles.warmup` 控制准备传送期间的粒子。可以关闭粒子，或修改粒子类型、数量和扩散范围。

## 语言文件

所有玩家消息和界面文字都位于 `plugins/KaTpa/lang/`。要使用自定义语言：

1. 复制一份现有语言文件，例如命名为 `my_lang.yml`。
2. 翻译需要修改的文本，不要改变节点名称。
3. 将 `config.yml` 中的 `language` 设置为 `my_lang`。
4. 执行 `/katap reload`。

## 数据存储

单服默认使用 SQLite，无需额外配置：

```yaml
storage:
  type: sqlite
```

群组服建议使用所有子服共享的 MySQL 或 MariaDB。修改 `storage.type` 或 `storage.mysql` 后必须重启服务器。

## 重载

修改普通功能设置或语言文件后执行：

```text
/katap reload
```

该指令需要 `katpa.admin` 权限。数据库类型和连接信息需要重启服务器才能生效。
