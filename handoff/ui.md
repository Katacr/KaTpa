# UI 交互层（ui）

> 最后更新：2026-09-20（列表空槽位自定义 empty-display/empty-actions；按钮级 permission；pwarp 删除入口）

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

- **列表条目颜色码未解析（2026-09-15）**：`GuiManager.buildItem` 对非列表按钮会 `ChatColor.translateAlternateColorCodes`，但 `KaTpaGuiListProvider` 预构建的列表条目（warp/home/pwarp/请求/在线玩家等）在 `renderMenu` 直接 `inv.setItem`，未做颜色翻译，导致名称/lore 中的 `&a` 等原样显示。修复：新增 `GuiManager.translateItemColors`，在放置列表条目时克隆物品并翻译 name/lore，与 `buildItem` 行为一致。
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
- `open: <menu>[:args]` 打开菜单（**继承当前会话变量**，便于子菜单标题引用来源列表项变量，如 `pwarp_owner_list` 的 `{owner_name}`）、`close` 关闭、`tell: <文本>`、`actionbar: <文本>`、`command: <指令>`、`console: <指令>`、`katpa: <namespace> <payload>`（业务动作）。

**变量**：`{key}` 会话/参数变量（如 `{mode_display}`/`{whitelist_count}`/`{members_name}`/`{warp_name}`）+ `%papi%` PlaceholderAPI 替换 + `&` 颜色码。

**实现类**（`src/main/java/.../ui/inventory/gui/`）：`GuiManager`（加载/渲染/动作）、`GuiMenu`（解析）、`GuiMenuHolder`、`MenuSession`、`GuiActionHandler`、`GuiListProvider`/`GuiListItem`、`KaTpaGuiActions`、`KaTpaGuiListProvider`。`InventoryMenuListener` 同时识别 `GuiMenuHolder` 与编程式 `InventoryMenu`。

**待办**：翻页（多页 layout）、请求接受/拒绝动作、聊天输入框（添加成员/新建地标/编辑字段）、`update` 定时刷新。详见 `INDEX.md` 的"GUI 资源驱动架构"节。

**修复记录（2026-09-12）：** pwarp 编辑 GUI 的 D（描述）/C（冷却）/F（费用）/N（名称）按钮无响应。根因：`pwarp_editor.yml` 的 `katpa: pwarp set <field> {pwarp_name}` 只给 3 段参数，`KaTpaGuiActions.handlePlayerWarpSet` 却按"值内联在第 4 段"解析，`args.length < 4` 直接静默 return。修复：四个分支改用 `ChatInputManager.capture` 提示输入（与管理员 `handleWarp` 一致），设置后刷新编辑器/管理器。`KaTpaGuiActions.java:230`。

**家编辑器（2026-09-15）：** 新增 `gui/home_editor.yml` 二级菜单（图标/更新位置/删除）；`home_selector.yml` 与 `home_manager.yml` 列表右键由「删除」改为 `katpa: home edit <name>` 打开编辑器。对应动作：`home edit/update/delete`；新增 `InteractionPlatform.showHomeEditor` 与 `HomeService.updateLocation`（保留描述/图标，仅更新坐标/世界/服务器）。图标/更新位置后经 `KaTpaGuiActions.reopenHomeMenu` 按来源菜单回编辑器或管理列表。

**列表空槽位自定义 + 按钮权限（2026-09-20）：** 带 `type` 的列表按钮支持 4 个**平级**主键，内部结构与前两者一致、互不混入：
- `display` / `actions`（原有，作用于列表条目）
- `empty-display` / `empty-actions`（新增，作用于**未使用槽位**：`empty-display` 结构与 `display` 相同，`empty-actions` 结构与 `actions` 相同，支持 `all`/`left`/`right`/`drop` 等）

实现：`GuiMenu.GuiButton` 新增 `emptyDisplay`/`emptyActions` 字段与 `emptyDisplay()`/`emptyActionsFor(clickType)`（只读平级键，不再从 display/actions 内取）；`GuiManager.renderMenu` 列表条目不足时对剩余槽位渲染 `empty-display`（未配置则留空、点击无动作）；`handleClick` 对登记的空槽位执行 `empty-actions`。

**空列表占位移除（2026-09-21）**：列表无任何数据时**不再插入 BARRIER 占位物品**，全部列表槽位登记为 `GuiListItem.Kind.BLANK`（不渲染、点击不执行任何动作）；删除 `emptyPlaceholder`/`renderPlaceholderSlot` 方法，并移除全部 `gui/*.yml` 的 `empty_text` 键。

**点亮/未点亮（2026-09-21）**：`GuiMenu.GuiButton` 新增 `litDisplay`/`unlitDisplay`（平级键 `lit-display`/`unlit-display`）；`GuiListItem.Kind` 新增 `LIT`/`UNLIT`，渲染时用对应 display、动作沿用按钮 `actions`。用于评分菜单 `PWARP_STARS`。

按钮级 `permission`：`GuiButton.permission()`（键 `permission`）；`GuiManager.canView(player, button)` 在渲染与点击时过滤无权限按钮（未配置则人人可见）。用于「管理员强行删除」按钮。

**pwarp 删除入口（2026-09-20）：** `pwarp_editor.yml` 新增 `R`（本人删除，`katpa: pwarp delete`）与 `Q`（管理员强行删除，按钮 `permission: katpa.pwarp.admin`）；`pwarp_selector` 列表项新增右键 `katpa: pwarp edit` 便于从主列表进入编辑器。删除最终仍走 `PlayerWarpService.delete`（`canEdit`=创建者或 `katpa.pwarp.admin`）。

**修复记录（2026-09-10）：** Hopper 漏斗窗口拒绝/接受按钮无响应。根因：`RequestHopperMenu.respond()` 使用 `p.performCommand("tpdeny " + requestId)` + `close()`，在库存关闭期间 `performCommand` 行为不可靠，命令可能未执行或执行不完整。修复：移除 `respond()` 方法，新增 `accept()`/`deny()` 两个私有方法，直接调用 `((KaTpaPlugin) plugin).requests().accept(p, request.id())` 和 `.deny(p, request.id())`，跳过命令解析层。同时移除未使用的 `UUID` 导入和 `render()` 中的 `requestId` 局部变量。

## 文本/图标能力（2026-09-18）

GUI 物品名/lore/标题、消息、actionbar 现统一经 `org.katacr.katpa.text.TextParser`（legacy+MiniMessage，支持 CE 字形与 `&item:[物品]` sprite）；物品 `material` 经 `util/ItemStacks` 支持 `ce:`/`ia:`/`oraxen:` 外部物品。详见 `INDEX.md` 同名小节。
