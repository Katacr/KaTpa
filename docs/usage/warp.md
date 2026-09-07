# 地标传送

管理员可以在服务器中设置公共地标，玩家通过 `/warp` 快速传送到这些位置。

> `/warp` 仅用于传送，不接受任何管理二级指令。所有地标编辑请使用 `/katap warp ...` 或 GUI 内的编辑器。

## /warp — 传送到地标

```text
/warp
/warp <名称>
```

不带名称时打开地标选择列表，列出当前可用的全部地标（含描述与图标）。带名称时直接传送到指定地标。

每个地标可以单独设置：

* **描述**：在地标列表中展示的说明文字
* **图标**：列表与编辑器中展示的物品（支持材质、`custom_model_data` 与 1.21.4+ 的 `item_model`）
* **权限**：只有持有该权限节点的玩家才能传送（留空则所有人可用）
* **冷却**：同一地标两次传送之间需要等待的秒数
* **费用**：传送时扣除的经济金额（需要安装 Vault 经济插件）

## /katap warp — 管理地标

```text
/katap warp edit <名称>
/katap warp create <名称>
/katap warp delete <名称>
/katap warp rename <旧名称> <新名称>
/katap warp icon <名称>
/katap warp set <name|permission|cooldown|cost|desc> <名称> [值]
```

所有 `katap warp` 子指令都需要 `katpa.warp.admin` 权限。打开编辑器 GUI 后可以直观地修改描述、图标、权限、冷却、费用，或重命名地标。在列表中右键地标也会打开编辑器（仅 `katpa.warp.admin` 可见）。

* `edit` 打开图形编辑器
* `create` 在当前位置创建地标
* `delete` 删除地标
* `rename` 重命名（旧名称 → 新名称）
* `icon` 用玩家手持物品设置图标（捕获材质、`custom_model_data`、1.21.4+ 的 `item_model`）
* `set permission <名称> [值]` 留空值即清除权限；`set desc <名称> <文本>` 支持多词描述；`set cooldown`/`set cost` 设置数值；`set name` 等价于 `rename`

## /setwarp 与 /delwarp — 命令式快捷操作

```text
/setwarp <名称>
/delwarp <名称>
```

`/setwarp` 在当前位置创建或更新地标；`/delwarp` 删除地标。这两者是命令式快捷入口，更完整的编辑（描述、图标、字段）请使用 `/katap warp`。

## 跨服地标传送

启用 `proxy.enabled` 后，地标传送可以跨服。如果目标地标在其他子服，KaProxy 会自动切换服务器并传送到精确坐标。跨服地标传送复用 KaProxy 的 Back 模块，无需额外配置。

## Vault 经济

地标费用为可选功能。安装了 Vault 及其经济插件后，玩家传送时自动扣除费用。未安装 Vault 时费用设置被忽略，玩家可以免费传送。
