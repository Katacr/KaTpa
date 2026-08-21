# 服务器配置

KaTpa 的功能设置位于 `plugins/KaTpa/config.yml`。

## 功能模块

每个功能模块可以独立开关。关闭的模块不会初始化服务、注册指令或响应事件，玩家执行对应指令时将提示"该功能已被管理员关闭"。

```yaml
modules:
  tpa:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    request-timeout-seconds: 30
    double-sneak-interval-seconds: 2
    cooldown:
      enabled: true
      seconds: 30
    allow-cross-world: true
    disabled-worlds: []
  back:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
  dback:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
  warp:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-permission: ""
    default-cooldown: 0
    default-cost: 0
  home:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
```

| 节点 | 默认值 | 用途 |
| --- | --- | --- |
| `modules.tpa.enabled` | `true` | 传送请求（/tpa、/tpahere、/tpaccept、/tpdeny、/tpacancel、/tpasetting） |
| `modules.tpa.request-timeout-seconds` | `30` | 请求等待多少秒后自动过期 |
| `modules.tpa.double-sneak-interval-seconds` | `2` | 双击潜行两次按键的最大间隔 |
| `modules.tpa.cooldown.enabled` | `true` | 是否启用请求冷却 |
| `modules.tpa.cooldown.seconds` | `30` | 两次有效请求之间需要等待的秒数 |
| `modules.tpa.allow-cross-world` | `true` | 是否允许跨世界传送 |
| `modules.tpa.disabled-worlds` | `[]` | 禁止使用 KaTpa 的世界名称 |
| `modules.back.enabled` | `true` | 返回上次位置（/back） |
| `modules.dback.enabled` | `true` | 返回死亡位置（/dback） |
| `modules.dback.default-amount` | `1` | 无 `katpa.dback.amount.<n>` 权限时的默认死亡位置保存数量 |
| `modules.warp.enabled` | `true` | 公共地标传送（/warp、/setwarp、/delwarp） |
| `modules.warp.default-permission` | `""` | 新建地标的默认权限节点，留空表示无限制 |
| `modules.warp.default-cooldown` | `0` | 新建地标的默认冷却秒数 |
| `modules.warp.default-cost` | `0` | 新建地标的默认传送费用 |
| `modules.home.enabled` | `true` | 个人家传送（/home、/sethome、/delhome） |
| `modules.home.default-amount` | `1` | 无 `katpa.home.amount.<n>` 权限时的默认家数量上限 |

模块开关修改后需要重启服务器才能生效。

## 传送吟唱、音效与粒子

以下三项为全局配置，定义所有模块共用的吟唱时间、音效和粒子。每个模块可通过 `modules.<模块>.warmup`、`modules.<模块>.sounds`、`modules.<模块>.particles` 控制是否启用。

| 节点 | 默认值 | 用途 |
| --- | --- | --- |
| `modules.<模块>.warmup` | `true` | 该模块是否启用传送吟唱 |
| `modules.<模块>.warmup-seconds` | `3` | 该模块的传送吟唱秒数 |
| `modules.<模块>.sounds` | `true` | 该模块是否启用交互音效 |
| `modules.<模块>.particles` | `true` | 该模块是否启用吟唱粒子 |

### 音效

三类音效可以分别启用、关闭或替换：

* `sounds.request-received`：收到请求时的提示音
* `sounds.countdown`：传送倒计时音效
* `sounds.teleport`：完成传送时的音效

每组音效都可以设置 `enabled`、`sound`、`volume` 和 `pitch`。

### 粒子

`particles.warmup` 控制吟唱期间的粒子。可以关闭粒子，或修改粒子类型、数量和扩散范围。

## 全局设置

| 节点 | 默认值 | 用途 |
| --- | --- | --- |
| `language` | `zh_CN` | 使用 `lang` 文件夹中的语言文件 |
| `server-id` | `local` | 子服显示名称，仅用于 UI 展示。跨服模式下真实服务器 ID 由 KaProxy 自动获取 |

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
