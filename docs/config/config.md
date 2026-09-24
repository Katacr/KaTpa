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
    min-distance: 16
    disabled-worlds: []
  dback:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    disabled-worlds: []
  warp:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    disabled-worlds: []
    default-permission: ""
    default-cooldown: 0
    default-cost: 0
    name-max-length: 32
    description-max-length: 100
  home:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
    bed-home: true
    bed-home-name: 重生点
    name-max-length: 32
    description-max-length: 100
    disabled-worlds: []
  pwarp:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
    total-slots: 10
    leaderboard-cache-size: 30
    create-cost:
      currency: money
      money: 0
      points: 0
    default-cost: 0
    cooldown-seconds: 30
    name-max-length: 32
    description-max-length: 100
    disabled-worlds: []
```

| 节点 | 默认值 | 用途 |
| --- | --- | --- |
| `modules.tpa.enabled` | `true` | 传送请求（/tpa、/tpahere、/tpaccept、/tpdeny、/tpacancel、/tpasetting） |
| `modules.tpa.request-timeout-seconds` | `30` | 请求等待多少秒后自动过期 |
| `modules.tpa.double-sneak-interval-seconds` | `2` | 双击潜行两次按键的最大间隔 |
| `modules.tpa.cooldown.enabled` | `true` | 是否启用请求冷却 |
| `modules.tpa.cooldown.seconds` | `30` | 两次有效请求之间需要等待的秒数 |
| `modules.tpa.allow-cross-world` | `true` | 是否允许跨世界传送 |
| `modules.tpa.disabled-worlds` | `[]` | 禁止发起或到达的世界名称 |
| `modules.back.enabled` | `true` | 返回上次位置（/back） |
| `modules.back.min-distance` | `16` | 同一世界内传送时，新位置与旧位置距离小于该值（格）则忽略本次返回点记录；`0` 表示始终记录 |
| `modules.back.disabled-worlds` | `[]` | 禁止返回的世界名称；在这些世界中也不会记录 `/back` 位置 |
| `modules.dback.enabled` | `true` | 返回死亡位置（/dback） |
| `modules.dback.disabled-worlds` | `[]` | 禁止返回的世界名称；在这些世界中也不会记录死亡位置 |
| `modules.warp.enabled` | `true` | 公共地标传送（/warp、/setwarp、/delwarp） |
| `modules.warp.disabled-worlds` | `[]` | 禁止传送到的世界名称；在这些世界中也不允许创建/更新地标 |
| `modules.warp.default-permission` | `""` | 新建地标的默认权限节点，留空表示无限制 |
| `modules.warp.default-cooldown` | `0` | 新建地标的默认冷却秒数 |
| `modules.warp.default-cost` | `0` | 新建地标的默认传送费用 |
| `modules.warp.name-max-length` | `32` | 地标名称最大长度（重命名/创建时校验） |
| `modules.warp.description-max-length` | `100` | 地标描述最大长度（设置描述时校验） |
| `modules.pwarp.enabled` | `true` | 玩家地标传送（/pwarp、/pw、/katap pwarp） |
| `modules.pwarp.default-amount` | `1` | 无 `katpa.pwarp.amount.<n>` 权限时的默认创建数量上限 |
| `modules.pwarp.total-slots` | `10` | 玩家地标列表展示的总槽位数（已有 + 空槽 + 锁定槽） |
| `modules.pwarp.leaderboard-cache-size` | `30` | 排行榜缓存容量（前 N 名）；创建/删除/评分或 `/pwarp admin reload` 时重算 |
| `modules.pwarp.create-cost.currency` | `money` | 创建玩家地标的货币：`money`（Vault 金币）或 `points`（PlayerPoints 点券） |
| `modules.pwarp.create-cost.money` | `0` | `currency=money` 时创建玩家地标消耗的金币（0 不收费） |
| `modules.pwarp.create-cost.points` | `0` | `currency=points` 时创建玩家地标消耗的点券（0 不收费） |
| `modules.pwarp.default-cost` | `0` | 新建玩家地标的默认传送费用 |
| `modules.pwarp.cooldown-seconds` | `30` | 玩家地标传送的全局冷却秒数（按玩家计时，冷却期间不可再次传送；0 不冷却） |
| `modules.pwarp.name-max-length` | `32` | 玩家地标名称最大长度（重命名/创建时校验） |
| `modules.pwarp.description-max-length` | `100` | 玩家地标描述最大长度（设置描述时校验） |
| `modules.pwarp.disabled-worlds` | `[]` | 禁止传送到的世界名称；在这些世界中也不允许创建/更新玩家地标 |
| `modules.home.enabled` | `true` | 个人家传送（/home、/sethome、/delhome） |
| `modules.home.default-amount` | `1` | 无 `katpa.home.amount.<n>` 权限时的默认家数量上限 |
| `modules.home.bed-home` | `true` | 玩家右键床时自动创建/覆盖名为 `bed-home-name` 的家（白天夜晚均可；重复右键同一张床不重复更新） |
| `modules.home.bed-home-name` | `重生点` | 右键床自动创建的家名称 |
| `modules.home.name-max-length` | `32` | 家名称最大长度（重命名/创建时校验） |
| `modules.home.description-max-length` | `100` | 家描述最大长度（设置描述时校验） |
| `modules.home.disabled-worlds` | `[]` | 禁止传送到的世界名称；在这些世界中也不允许创建/更新家 |

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
| `server-id` | `local` | 子服显示名（服务器别名）。创建家/地标时与 KaProxy 真实服名一并存入数据库，菜单与提示中展示该别名；跨服传送/事务仍使用代理下发的真实服名。留空则回退真实服名 |

## 世界与服务器显示名

菜单与消息中展示的世界名、服务器名会自动「本地化」，便于玩家理解：

* **世界名**：若服务器安装 **Multiverse-Core**（可选依赖），优先显示该世界的别名（alias）；未设置别名或未安装时回退 Bukkit 世界名。
* **服务器名**：创建家/地标时同时记录 KaProxy 真实服名（用于传送/事务）与 `server-id`（显示别名）；菜单与提示展示 `server-id`，未配置时回退真实服名。

Multiverse-Core 通过反射调用其 API，作为可选依赖，未安装时功能不受影响。

## 语言文件

所有玩家消息和界面文字都位于 `plugins/KaTpa/lang/`。要使用自定义语言：

1. 复制一份现有语言文件，例如命名为 `my_lang.yml`。
2. 翻译需要修改的文本，不要改变节点名称。
3. 将 `config.yml` 中的 `language` 设置为 `my_lang`。
4. 执行 `/katap reload`。

其中 `gui.list.*` 为**列表菜单条目**（地标/家/玩家地标/请求/在线玩家/名单等）的名称与描述文本，`gui.prompt.*` 为菜单内修改字段时的聊天输入提示，`gui.request-menu.*` 为传送请求漏斗窗口，`chat-input.*` 为聊天输入取消/超时提示。菜单按钮本身的文本位于 `plugins/KaTpa/gui/*.yml`（见“菜单”一节）。

## 数据存储

单服默认使用 SQLite，无需额外配置：

```yaml
storage:
  type: sqlite
```

群组服建议使用所有子服共享的 MySQL 或 MariaDB。修改 `storage.type` 或 `storage.mysql` 后必须重启服务器。

KaTpa 会定期检查长期数据库连接，并在 MySQL/MariaDB 因空闲超时或短暂中断而关闭连接后自动重连。所有存储模块的 JDBC 操作会被串行化，避免共享连接上的并发事务互相干扰。为避免金额累加等操作被重复执行，执行途中的失败写入不会自动重放；连接会被标记失效，下一次数据库操作将使用新连接，并在后台记录原失败。

## 重载

修改普通功能设置或语言文件后执行：

```text
/katap reload
```

该指令需要 `katpa.admin` 权限。数据库类型和连接信息需要重启服务器才能生效。

## 文本与图标格式（GUI 物品、标题、消息通用）

- **颜色**：支持传统颜色码（`&a`、`&l`、`&#RRGGBB`）与 **MiniMessage**（如 `<white>`、`<gradient:#ff0000:#00ff00>`）。文本含 MiniMessage 标签时按 MiniMessage 解析，否则按传统颜色码解析。
- **CraftEngine 字形**：`<image:命名空间:ID>`、`<shift:像素>` 会原样保留，由 CraftEngine 拦截数据包时替换；需保持 CE 的 `network.intercept-packets` 中 `container`（GUI）与 `item` 为 `true`。
- **内联物品图标**：`&item:[物品]`（需服务端 MC 1.21.9+）。`物品` 可为原版材质名（如 `diamond`）或外部物品 ID（`ce:general:pass`、`ia:命名空间:ID`、`oraxen:ID`），自动解析其二维纹理；解析失败时该标签会被移除。复杂/专用图标可用插件目录 `item_sprites.yml` 覆盖：
  ```yaml
  sprites:
    'ce:general:pass': 'items:general:item/pass'
    'oraxen:complex_staff': { atlas: 'minecraft:blocks', sprite: 'oraxen:item/complex_staff_icon' }
  ```
- **物品材质**：GUI 配置里的 `material` 支持外部物品前缀 `ce:` / `craftengine:`、`ia:` / `itemsadder:`、`oraxen:`（如 `material: "ce:general:pass"`）；解析失败回退原版材质或默认材质。

> 旧服务端（低于 1.21.9 或无新版 Adventure）会自动降级：MiniMessage 回退为传统颜色码，`&item:[...]` 标签被移除，不影响功能。

