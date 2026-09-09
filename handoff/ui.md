# UI 交互层（ui）

> 最后更新：2026-09-07

## 现状

- `InteractionPlatform` 接口（`ui/InteractionPlatform.java:21-68`）定义平台中立的 UI 方法，避免 Spigot 加载时触碰 Paper 专属 Dialog 类。
- `InteractionService`（`ui/InteractionService.java`）探测并选择实现：`createPlatform` 反射探测 `io.papermc.paper.dialog.Dialog` → Paper，否则 `net.md_5.bungee.api.dialog.Dialog` + `PlayerCustomClickEvent` → Spigot，否则抛异常。
- `platformName()` 返回 "Paper"/"Spigot" 用于启动日志。

## 两套 Dialog 实现差异

| 维度 | Paper（`ui/paper/PaperInteractionPlatform`） | Spigot（`ui/spigot/SpigotInteractionPlatform`） |
| --- | --- | --- |
| Dialog API | 原生 `io.papermc.paper.dialog.*` | Bungee `net.md_5.bungee.api.dialog.*` |
| 按钮回调 | Adventure `ClickEvent.callback` + `ClickCallback.Options`（单次、lifetime） | 自建一次性 `custom-click session`，经 `PlayerCustomClickEvent` 校验 |
| 额外监听 | 无 | 实现 Listener，注册 `PlayerCustomClickEvent` + `PlayerQuitEvent` |
| ActionBar | `player.sendActionBar` | `player.spigot().sendMessage(ChatMessageType.ACTION_BAR, BungeeComponentSerializer...)` |
| 富文本 | 直接 Adventure Component | 全程 `BungeeComponentSerializer` 转 BaseComponent |
| 关闭 Dialog | 反射 `closeDialog` 发送 `ClientboundClearDialogPacket` | `receiver.clearDialog()` |

两者业务回调都 `Bukkit.getScheduler().runTask` 回主线程，复用 `RequestService`/`SettingsStore`。

## 三种接受方式（dialog/chat/sneak）

由 `presentRequest` 据接收者偏好分派：
- **DIALOG**：`showRequestList` 渲染带同意/拒绝按钮的 Dialog 列表。
- **CHAT**：`showChatActions` 发送可点击文本 `ClickEvent.runCommand("/tpaccept <id>")`/`/tpdeny`。
- **SNEAK**：仅提示文本 + `sneak-hint` 键，双击潜行逻辑在 `PlayerListener` 处理。
- 设置界面下拉选 `mode`，保存持久化到 `SettingsStore`。

## 踩过的坑

- 单 JAR 必须反射探测平台类，避免 Spigot 服务端加载时因缺失 Paper Dialog 类触发 LinkageError（**已废弃：2026-09-07 迁移到 Inventory，移除 Dialog 探测**）。
- Dialog 列表只在新增/处理/真正超时推送刷新，不定时刷新，避免刷屏；最后一条关闭后自动关 Dialog，不显空列表（**Inventory 版需重新实现此行为**）。

## 2026-09-07 架构迁移：Dialog → Inventory

**决策**：迁移到原生容器（Chest/Anvil）库存菜单，兼容 1.16.5 + Java 16，参考 KaMenu 的 Container 框架自建轻量 Inventory 框架（不引入 KaMenu 插件依赖）。

**新建文件**（`src/main/java/.../ui/inventory/`）：
- `InventoryMenu.java`：容器菜单基类，封装 `Bukkit.createInventory` + `InventoryHolder`，注册槽位点击回调，全局监听器按 holder 路由。
- `InventoryMenuListener.java`：单一全局 `InventoryClickEvent`/`InventoryCloseEvent` 监听，路由到活跃菜单。
- `InventoryMenuRegistry.java`：`UUID → InventoryMenu` 映射。
- `ItemBuilder.java`：`&` 颜色码物品构建工具。
- `InventoryInteractionPlatform.java`：实现 `InteractionPlatform`，目前各方法为占位菜单（`PlaceholderMenu`），待逐步填充。

**改造**：
- `InteractionService`：移除 `createPlatform` 反射探测，直接 `new InventoryInteractionPlatform(plugin)`；`PlatformNamed` 抽为包级接口（`ui/PlatformNamed.java`）。
- `InteractionPlatform` 接口签名保留 `Component`（Adventure），由 `InventoryInteractionPlatform` 用 `LegacyComponentSerializer` 转 legacy 字符串。

**待办**：填充各菜单（请求列表/玩家选择/设置/名单/地标/家）的真实 Inventory 实现。详见 `INDEX.md` 的"重大架构迁移"节。

## GUI 资源驱动（新增 2026-09-07）

**决策**：从 `resources/gui/*.yml` 加载菜单，插件解析渲染，首次开服提取到 `plugins/KaTpa/gui/`。参考 KaMenu Container 语法与 KaGuilds-refactor 的 `gui_CN/`。语法键名用 `button` 单数（KaGuilds 风格）。

**资源结构**（`src/main/resources/gui/`）：
- 根：`title`（`&` 颜色码）、`Layout`（每行一个字符串，字符→槽位，空格=空）、`button`（字符→按钮定义）。
- 按钮：`display`（material/name/lore/amount/custom_model_data/skull_owner/item_model/custom_data）、`actions`（left/right/all → 动作字符串列表）、`type`（列表型标识，如 `REQUEST_LIST`/`ONLINE_PLAYERS`/`WARP_LIST`/`HOME_LIST`/`RELATION_LIST`）。
- `display.custom_model_data` 与 `display.custom_data` 等价（都映射到 `ItemMeta.setCustomModelData`）；`display.item_model` 为 `"namespace:key"` 字符串，映射到 1.21.4+ 的 `ItemMeta.setItemModel(NamespacedKey)`，旧版本静默忽略。`item_model`/`custom_data`/`custom_model_data` 均支持 `{key}` 变量替换（如 `material: "{warp_icon}"` 配合 `item_model: "{warp_item_model}"`）。

**动作语法**：
- `open: <menu>[:args]` 打开菜单、`close` 关闭、`tell: <文本>`、`actionbar: <文本>`、`command: <指令>`、`console: <指令>`、`katpa: <namespace> <payload>`（业务动作）。

**变量**：`{key}` 会话/参数变量（如 `{mode_display}`/`{whitelist_count}`/`{members_name}`/`{warp_name}`）+ `%papi%` PlaceholderAPI 替换 + `&` 颜色码。

**实现类**（`src/main/java/.../ui/inventory/gui/`）：`GuiManager`（加载/渲染/动作）、`GuiMenu`（解析）、`GuiMenuHolder`、`MenuSession`、`GuiActionHandler`、`GuiListProvider`/`GuiListItem`、`KaTpaGuiActions`、`KaTpaGuiListProvider`。`InventoryMenuListener` 同时识别 `GuiMenuHolder` 与编程式 `InventoryMenu`。

**待办**：翻页（多页 layout）、请求接受/拒绝动作、聊天输入框（添加成员/新建地标/编辑字段）、`update` 定时刷新。详见 `INDEX.md` 的"GUI 资源驱动架构"节。

**修复记录（2026-09-10）：** Hopper 漏斗窗口拒绝/接受按钮无响应。根因：`RequestHopperMenu.respond()` 使用 `p.performCommand("tpdeny " + requestId)` + `close()`，在库存关闭期间 `performCommand` 行为不可靠，命令可能未执行或执行不完整。修复：移除 `respond()` 方法，新增 `accept()`/`deny()` 两个私有方法，直接调用 `((KaTpaPlugin) plugin).requests().accept(p, request.id())` 和 `.deny(p, request.id())`，跳过命令解析层。同时移除未使用的 `UUID` 导入和 `render()` 中的 `requestId` 局部变量。
