# KaTpa 交接文件索引

> 项目：KaTpa —— 面向 Paper 1.21.7 / Spigot 1.21.6+、JDK 21 的玩家传送插件
> 最后更新：2026-09-21（`/pw`/`/pwarp admin`；排行榜缓存；PlayerPoints 创建收费；pwarp 全局冷却；移除迁移代码；编辑器更新位置；列表文本 i18n；评分点亮/提交；各模块世界黑名单）

## 项目概览

- **技术栈**：Gradle（Kotlin DSL）+ ShadowJar，单 JAR 同时编译 `src/main`（Paper 适配器）与 `src/spigot`（Spigot 适配器）两个 sourceSet。
- **运行平台**：启动时通过 `Class.forName` 反射探测 `io.papermc.paper.dialog.Dialog` 或 `net.md_5.bungee.api.dialog.Dialog`，自动选择 Paper 原生 Dialog 或 Spigot Bungee Dialog 实现，单 JAR 跨平台启动。
- **依赖下载**：仅内置 Libby（`net.byteflux:libby-bukkit`），首次开服按 `storage.type`（sqlite/mysql）下载 SQLite JDBC 或 MariaDB JDBC 到服务器 `libraries/` 目录。
- **当前版本**：`1.2.1`（`build.gradle.kts:8`）。
- **部署状态**：1.2.1 已于 2026-09-16 部署到 Lobby 并重启验证；其余 6 个后端仍为 1.2.0，尚未同步。
- **构建**：`./gradlew clean build` → `build/libs/KaTpa-1.0.0.jar`（注意 archiveClassifier 为空，实际文件名随 version 变）；本地测试服 `./gradlew runServer`（1.21.7）。

## 模块划分与文档

| 模块名 | 目录/文件 | 交接文件 |
| --- | --- | --- |
| 核心入口与装配 | `KaTpaPlugin.java` | `core.md` |
| 数据模型与存储 | `model/`、`storage/` | `storage.md` |
| 服务与命令 | `service/`、`command/`、`listener/` | `services.md` |
| UI 交互层（Dialog/Chat/Sneak） | `ui/`（含 spigot 适配器） | `ui.md` |
| 跨服网络（KaProxy） | `network/` | `network.md` |
| 配置与语言 | `util/`、`resources/config.yml`、`resources/lang/` | `config.md` |
| 占位符扩展（新） | `placeholder/` | 见 `services.md` |

## 调研报告

- [项目整体架构调研](research/overview.md)
- [数据模型与存储层调研](research/storage.md)
- [服务层与命令层调研](research/services.md)
- [UI 交互层与网络层调研](research/ui-network.md)

## 当前进行中的工作（工作区未提交改动）

- 跨服传送（back/dback/home/warp）重构：**先在本服完成吟唱（`beginDirect`）再请求切服**，与单服传送行为对齐。改动文件：`BackService.java`、`DbackService.java`、`HomeService.java`、`WarpService.java`、`KaTpaPlugin.java`（+11 行，疑似 placeholder 注册相关）。
- 新增 `placeholder/` 目录（KaTpaPlaceholderExpansion）+ 文档 `docs/perm/placeholders.md`、`docs-en/perm/placeholders.md`，均未提交。
- 注意：Git 对部分文件报 "LF will be replaced by CRLF" 换行符警告，提交前需留意换行符一致性。

## 重大架构迁移：Dialog → Inventory（进行中）

> 决策日期：2026-09-07。详见 `ui.md` 与 `config.md`。

**背景**：原架构依赖 Paper 原生 Dialog 与 Spigot Bungee Dialog（限制版本 ≥ 1.21.6），局限性大。
**目标**：迁移到原生容器（Chest/Anvil）库存菜单，兼容 **Minecraft 1.16.5 + Java 16**，参考团队 KaMenu 项目的 Container 框架思路自建轻量 Inventory 框架（不引入 KaMenu 插件依赖）。

### 已完成（骨架阶段，构建通过）
- `build.gradle.kts`：toolchain→21（构建用），字节码 `release=16`；依赖降到 `spigot-api:1.16.5` 为基准；移除双 Dialog adapter sourceSet（spigot/paper），改为单 main 编译。
- `plugin.yml`：`api-version: '1.16'`。
- 新建 `src/main/java/.../ui/inventory/`：`InventoryMenu`（基类）、`InventoryMenuListener`（全局点击分发）、`InventoryMenuRegistry`、`ItemBuilder`、`InventoryInteractionPlatform`（实现 InteractionPlatform）。
- `InteractionService`：移除 Dialog 反射探测，直接实例化 `InventoryInteractionPlatform`；`PlatformNamed` 抽为包级接口。
- 删除旧 `src/main/.../ui/paper/` 与 `src/spigot/` Dialog 适配器。
- 修复 1.21.7→1.16.5 兼容性：`teleportAsync`→同步 `teleport`；`List.getFirst()`/`removeLast()`→`get(0)`/`remove(size-1)`；`PlayerMoveEvent.hasChangedPosition()`→坐标比较；`Player.sendActionBar(String)`→`player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(...))`。

### 待办
- 各 UI 方法目前是占位菜单（PlaceholderMenu），需逐步填充：请求列表、玩家选择器、设置、名单编辑、地标/家选择与管理。
- Spigot 1.16.5 运行时需确保 Adventure 由 Libby 挂载（当前 `compileOnly`，Paper 1.16.5 内置，Spigot 端待验证）。
- 构建需 JDK 21 运行 Gradle（环境 `JAVA_HOME=C:\Program Files\Java\jdk-21`），字节码产出为 Java 16。

## GUI 资源驱动架构（新增，2026-09-07）

> 参考 KaMenu Container 语法与 KaGuilds-refactor 的 `gui/` 实现。详见 `ui.md` 的"GUI 资源驱动"节。

**决策**：创建 `resources/gui/*.yml` 菜单资源，插件内部解析渲染；首次开服提取到 `plugins/KaTpa/gui/` 供玩家自定义。语法沿用 Ka 系列风格（`title`/`Layout`/`button` 单数/`display`/`actions`/`open:`/`condition`/`type` 列表型）。

**新增文件**（`src/main/java/.../ui/inventory/gui/`）：
- `GuiMenu.java`：菜单定义解析（title/layout/buttons，按钮含 type/display/actions）。
- `GuiManager.java`：加载 gui/ YAML、提取到磁盘、渲染 InventoryMenu、执行动作（`open`/`close`/`tell`/`actionbar`/`command`/`console`/`katpa:`）。
- `GuiMenuHolder.java` / `MenuSession.java`：库存持有者与会话变量。
- `GuiActionHandler.java`：业务动作接口（`katpa: <namespace>`）。
- `GuiListProvider.java` + `GuiListItem`：列表型按钮数据接口（REQUEST_LIST/ONLINE_PLAYERS/WARP_LIST/HOME_LIST/RELATION_LIST）。
- `KaTpaGuiActions.java`：业务动作实现（setting/relation/warp/home）。
- `KaTpaGuiListProvider.java`：列表数据实现。

**菜单资源**（`src/main/resources/gui/`，9 个）：settings / request_list / player_selector / relation_editor / warp_selector / warp_manager / warp_editor / home_selector / home_manager。

**已验证**：`./gradlew build` 成功，9 个 YAML 正确打包进 JAR；列表型按钮通过 `type` 字段识别并由 `KaTpaGuiListProvider` 填充。

**第二批完善（2026-09-07 续）**：
- 5 个列表菜单均已声明 `type` 按钮（REQUEST_LIST/ONLINE_PLAYERS/WARP_LIST/HOME_LIST/RELATION_LIST），列表项点击触发对应动作。
- 列表项动作接好：`request_list` 左键 `tpaccept <id>`/右键 `tpdeny <id>`；`player_selector` 左键 `katpa: request send`；`warp_selector`/`warp_manager` 左键传送/右键删除；`home_selector`/`home_manager` 左键传送/右键删除；`relation_editor` 左键移除成员。
- `KaTpaGuiActions` 补 `request send`/`warp delete`/`home delete`/`relation remove`；`relation add`/`warp create`/`home create`/`warp set` 通过 `ChatInputManager`（聊天捕获，输入 cancel 取消，60s 超时）实现输入流程。
- `ChatInputManager.java`：聊天输入捕获器，注册 `AsyncPlayerChatEvent` + `PlayerQuitEvent`。
- `settings`/`warp_editor`/`relation_editor` 打开前注入展示变量（mode_display/whitelist_count/blacklist_count/warp_* 等）。

**待办（仍未实现）**：
- 列表菜单翻页（多页 layout，`<`/`>` 导航）尚未实现（当前单页 4 行列表区）。
- `update` 定时刷新（`update` 键）尚未实现。
- 跨服列表（在线玩家来自 KaProxy）尚未接入 `ONLINE_PLAYERS` 提供器。
- 请求列表的"自动关闭空列表"行为（原 Dialog 版）尚未在 Inventory 版还原。

**第三批完善（2026-09-07 续）**：
- 列表分页：所有列表菜单加 `<`/`>` 翻页按钮，动作 `katpa: page prev/next`；`GuiListProvider.provide` 增加 `page`/`perPage` 参数，`KaTpaGuiListProvider` 按页截取；`MenuSession` 加 `page` 字段；`GuiManager` 注入 `{page}`/`{total_pages}` 变量并加 `reopen()`；翻页按钮名称显示 `({page}/{total_pages})`。
- `update` 定时刷新：菜单 YAML 根 `update: <ticks>` 时启动定时重渲染（`scheduleRefresh`），关闭/重新打开时取消旧任务。
- 跨服在线玩家：`buildOnlinePlayers` 优先用 `plugin.network().onlinePlayers()`（KaProxy 全服），回退本服；列表项 lore 显示服务器名。

**已实现功能总览**：设置/请求列表(接受·拒绝)/玩家选择(本服+跨服)/地标(选择·管理·编辑·删除)/家(选择·管理·删除)/名单(查看·添加·移除) 全部可用；聊天输入捕获（添加成员/新建地标/家/编辑字段）；翻页；定时刷新。

**第四批完善（2026-09-07 续）**：
- 翻页越界 clamp：`handlePage` 的 `next` 用 `total_pages` 限制最大页，避免空白页。
- `total_pages` 计算重构：循环外统一取各 type 最大值，避免多 type 字符互相覆盖。
- 空列表占位：列表无任何条目时在第 1 个槽位放 BARRIER + `empty_text`（各菜单已配置"暂无待处理请求/没有在线玩家/暂无地标/暂无家/名单暂无成员"等）。
- 请求列表加 `update: 20`（1 秒）定时刷新，新请求即时显示；关闭菜单自动取消定时任务。

**剩余可优化（非阻断）**：
- 翻页后按钮名 `{page}/{total_pages}` 刷新依赖 reopen，正常。
- 地标/家编辑目前直接调 KaTpa 自带 `setwarp`/`sethome` 命令改 store，已正确接线，无需额外代码。

**第五批完善（2026-09-07 续）—— warp/home 图标与描述**：
- `Warp`/`Home` record 增加 `UUID id`（主键，替代旧自然键 name）、`description`、`iconMaterial`、`iconCustomData`、`iconItemModel` 字段；`DEFAULT_ICON` 分别用 `ENDER_PEARL`/`RED_BED`。
- `WarpStore`/`HomeStore`：表改用 `id` UUID 主键 + `name` 唯一索引双索引；新增 `icon_*` 三列、`description` 列；`WarpStore` 加 `rename(Warp, newName, now)` 与 `find(UUID)`。
- `WarpService`/`HomeService`：加 `WarpBuilder`/`HomeBuilder` 可变构造器、`setDescription`/`rename`/`setIcon`；`setIcon` 从手中物品读 material+customModelData+itemModel。
- `WarpCommand` 加 `setdesc`/`rename`/`seticon` 子命令（`/warp setdesc <name> <文本>`、`/warp rename <old> <new>`、`/warp seticon <name>`）。
- `KaTpaGuiListProvider`：`buildIconItem` 按图标字段构造展示物品（material→customModelData→itemModel）；列表项 lore 注入 `warp_description`/`home_description`。
- `KaTpaGuiActions`：`handleWarp`/`handleHome` 重写，支持 set name/permission/cooldown/cost/desc + icon + rename。
- `warp_editor.yml` 加 D（描述）/I（图标）按钮；`config.yml` 加 `modules.warp.name-max-length`/`description-max-length`、`modules.home.name-max-length`/`description-max-length`（默认 32/100）。
- `InventoryInteractionPlatform.showWarpEditor` 注入 `warp_description`/`warp_icon` 变量。

**item_model 跨版本方案（参考 KaGuilds-refactor）**：
- `item_model` 是 Paper 1.21.4+ 才有的 API（`ItemMeta.getItemModel()`/`setItemModel(NamespacedKey)`），编译目标 `spigot-api:1.16.5` 无此 API → 用反射调用。
- `KaTpaGuiListProvider` 提供 `readItemModelReflect(ItemMeta)`（静态）与 `setItemModelReflect(ItemMeta, String)`：用 `Class.forName("org.bukkit.inventory.meta.ItemMeta")` + `Class.forName("org.bukkit.NamespacedKey")` 反射调用，1.16.5/旧版抛 `ReflectiveOperationException` 静默跳过。`setItemModelReflect` 解析 `"namespace:key"` 格式构造 NamespacedKey。
- 参考实现：KaGuilds-refactor `src/main/kotlin/.../service/GuildService.kt:1698 getItemModel` 与 `MenuManager.kt:158 setItemModel`（含 1.21.4 版本检查 + 旧版降级 CustomModelData）。KaTpa 当前未做版本检查，旧版直接跳过（足够，因本服为 1.21.x）。
- `GuiManager.buildItem` 的 `material` 字段现已经过 `resolveText`，故 `warp_editor.yml` 的 `material: "{warp_icon}"` 可正确替换为实际图标材质。

## 环境注意

- 系统默认 `java` 为 JDK 25，与 Gradle 8.14.3 不兼容；构建须 `JAVA_HOME=C:\Program Files\Java\jdk-21` 或 `jdk-16`。
- 切勿用 PowerShell `Set-Content` 改写含中文注释的源文件（会丢失 UTF-8 编码），改用 edit 工具。

## 文档同步状态（2026-09-07）

- `docs/`（中）与 `docs-en/`（英）已同步以下改动：
  - **指令**：`/warp` 纯传送（无二级管理指令）；新增 `/katap warp edit|create|delete|rename|icon|set <...>` 管理指令（`commands.md` 管理员表）；`commands.md` 玩家表中 `/warp` 注明"仅传送"。
  - **权限**：`katpa.warp.admin` 扩展为涵盖 `/katap warp ...`（`permissions.md`）。
  - **占位符**：新增 `%katpa_mode%`（通用段）；PlaceholderAPI 占位符页面已从 `perm/placeholders.md` 移至根目录 `placeholders.md`（中/英），并从 `perm/README.md` 与 `SUMMARY.md` 的 `perm` 子树下移出。
  - **使用指南**：`warp.md` 重写——地标支持描述/图标，`/katap warp` 管理指令详解，纯传送说明；`home.md` 补充描述/图标说明。
  - **配置**：`config.md`（中/英）YAML 示例与节点表新增 `modules.warp.name-max-length`/`description-max-length`、`modules.home.name-max-length`/`description-max-length`（默认 32/100）。
- 注：`docs/usage/settings.md` 与 `docs-en/usage/settings.md` 仍引用"对话框"等旧 UI 术语（Inventory 迁移前遗留），待 UI 文档整体修订时统一。

## 文档同步状态（2026-09-09）

- `docs/usage/pwarp.md`（中/英）：新增「历史传送 / 按玩家筛选 / 收藏」三节，主列表列表操作更新（右键=收藏切换，评分改由排行榜入口），并说明三者交互与入口按钮 H/P/F。
- `docs/perm/commands.md` 与 `docs/perm/permissions.md`：本次未新增指令/权限（收藏/历史/筛选均为 GUI 动作），无需改表；保持 `rate` 指令说明不变。

## 玩家公共地标模块（player_warp，2026-09-07 新增）

> 独立于现有 warp 模块。玩家可创建属于自己的公共地标，他人可浏览/传送/评分，创建者获得传送收入（离线挂账）。

**新增文件：**
- `model/PlayerWarp.java`：record（id/ownerId/ownerName/name/server/serverId/world/worldAlias/x/y/z/yaw/pitch/description/icon*/cost/createdAt）。
- `storage/PlayerWarpStore.java`：独立表 `player_warp` + owner 二级索引 + `byOwner`/`count`/`find(ownerId,name)`。
- `storage/WarpRatingStore.java`：评分表 `player_warp_rating`（PK player_uuid+warp_id）、离线收入表 `player_warp_pending_income`、`scoreOf` 权重（5★+10/4★+5/3★+1/2★-5/1★-10）、`leaderboard(limit)`、`addPendingIncome`/`takePendingIncome`。
- `service/PlayerWarpService.java`：创建（数量上限 `katpa.pwarp.amount.<n>`）/传送（创建者免 cost）/编辑/评分/收入结算（在线 deposit，离线挂账）。
- `command/PlayerWarpCommand.java`：`/pwarp` 传送 + 列表。
- `resources/gui/`：`pwarp_selector`/`pwarp_manager`/`pwarp_editor`/`pwarp_rate`/`pwarp_leaderboard`（新增 PWARP_LIST / PWARP_LEADERBOARD 列表类型）。
- 扩展：`KaTpaCommand`（handlePlayerWarp）、`KaTpaGuiListProvider`（buildPlayerWarps/buildPwarpLeaderboard）、`KaTpaGuiActions`（handlePlayerWarp/do rate）、`InteractionPlatform`/`InteractionService`/`InventoryInteractionPlatform`（showPwarp*）、`PlayerListener.onJoin`（领取离线收入）、`KaTpaPlugin`（模块初始化/访问器/命令注册/disable 关闭 store）。
- `plugin.yml`：`pwarp` 命令 + 权限 `katpa.pwarp.use`/`create`/`admin`；`katpa.pwarp.amount.<n>` 动态权限。
- `config.yml`：`modules.pwarp.*`（enabled/default-amount/total-slots/leaderboard-cache-size/create-cost/default-cost/cooldown-seconds/name-max-length/description-max-length）。
- `lang/zh_CN.yml`：`pwarp-*` 消息键（约 30 条）。

**权限模型：** `katpa.pwarp.create`（默认 true）创建；`katpa.pwarp.amount.<n>` 数量上限（默认 1）；`katpa.pwarp.admin`（OP）可编辑/删除任意玩家地标（清理违规）。

**离线收入事务：** 他人传送付费时，若创建者在线直接 `depositPlayer`；离线（含跨服不在任意子服）则 `addPendingIncome` 累加挂账，创建者下次 `PlayerJoinEvent`（任意子服）`claimPendingIncome` 领取并提示。

**文档：** `docs/usage/pwarp.md`（中/英）、`docs/perm/commands.md`（玩家地标指令段）、`docs/perm/permissions.md`（pwarp 权限）、`docs/config/config.md`（modules.pwarp 表格+示例）、`docs/SUMMARY.md`（usage 登记）。

**增强（2026-09-09）：历史传送 / 玩家筛选 / 收藏**
- **历史传送**：成功传送（本地+跨服落点成功）后 `recordVisit` 写入 `player_warp_history`（PK player_uuid+warp_id，去重保留最近访问时间）；菜单 `pwarp_history` 按访问倒序列出，可再次传送。
- **玩家筛选**：菜单 `pwarp_players` 列出所有拥有地标的创建者（`PwarpMetaStore.ownerIds` 从 `player_warp` 取 DISTINCT owner_id，名称走 `SettingsStore.knownName`），点击进入 `pwarp_owner_list:{owner_uuid}`（PWARP_OWNER_LIST，按名称排序）。
- **收藏**：新增 `player_warp_favorite` 表；`pwarp` 列表项（含主列表/历史/玩家筛选）按 **Q 键（丢弃键）**切换收藏（左键传送）；`pwarp_favorites` 菜单按 Q 键取消收藏。动作 `katpa: pwarp favorite <name>` → `PlayerWarpService.toggleFavorite`（切换后 `gui.reopen` 刷新图标/收藏态）。
- **新增文件/扩展**：
  - `storage/PwarpMetaStore.java`：两张新表 + `recordVisit`/`history`/`toggleFavorite`/`isFavorite`/`favorites`/`ownerIds`/`byOwner`；`KaTpaPlugin` 新增字段与访问器 `pwarpMeta()`、`onEnable` 初始化、`onDisable` 关闭。
  - `KaTpaGuiListProvider`：`PWARP_HISTORY`/`PWARP_FAVORITE`/`PWARP_OWNERS`/`PWARP_OWNER_LIST` 四种列表类型 + 共享 `buildPwarpItem`（收藏态 lore/actions）；`buildPlayerWarps`（主列表）右键改为收藏切换。
  - `KaTpaGuiActions.handlePlayerWarp`：新增 `favorite` 子动作。
  - `InventoryMenuListener`：识别 `ClickType.DROP`/`CONTROL_DROP` → clickType `"drop"`，YAML 收藏动作从 `right` 改为 `drop`；`GuiMenu.actionsFor` 已支持任意 clickType 键。
  - YAML：`pwarp_selector`（加 H/P/F 三入口）、`pwarp_history`/`pwarp_favorites`/`pwarp_players`/`pwarp_owner_list` 四个新菜单；`gui/` 资源每次启动 `saveResource(..., true)` 强制覆盖。
  - `lang/zh_CN.yml`：`pwarp-favorited`/`pwarp-unfavorited`。
- **交互取舍**：主列表/PWARP_LIST 右键由"评分"改为收藏切换，评分入口迁至排行榜 `pwarp_leaderboard`（右键地标进评分菜单）；收藏触发键最终定为 **Q 键**（`drop`，避免右键占用评分入口），lore 显示 `[Q键]`。
- 待部署重启验证：新建 `player_warp_history`/`player_warp_favorite` 两表；历史/收藏/玩家筛选菜单跳转与 Q 键收藏切换。

**修复记录（2026-09-08）：** 初版部署后玩家地标功能完全无效，日志报 `未注册的 katpa 动作命名空间: pwarp`。根因：`InventoryInteractionPlatform.initialize` 注册 GUI 动作命名空间时只注册了 setting/relation/warp/home/request/page 六个，**漏注册 `pwarp`**，导致所有 `katpa: pwarp ...` 菜单动作无法分发。修复：在 `warp` 注册后补 `gui.registerActionHandler("pwarp", actions)`（`InventoryInteractionPlatform.java:54`）。教训：新增 GUI 动作命名空间时，`KaTpaGuiActions.execute` 的 `switch` 分支与 `registerActionHandler` 注册必须同步。

**修复记录（2026-09-09）：** 运行时报 `Table 'katpa.home' doesn't exist`（`/sethome` 写入失败）。根因：MySQL/MariaDB 严格模式下建表语句中 `TEXT NOT NULL DEFAULT ''` 列定义不合法（TEXT 类型不允许 DEFAULT），导致 `CREATE TABLE IF NOT EXISTS` 在启动时抛 `SQLException`（仅 severe 一行日志，插件继续运行），表从未建成。修复：`HomeStore`/`WarpStore`/`PlayerWarpStore` 的 **MySQL 分支**建表语句去掉 TEXT 列的 `DEFAULT ''`（改为 `TEXT NOT NULL`，代码层已有 null→"" 兜底）；SQLite 分支保留 DEFAULT（SQLite 支持无碍）。已重新 `shadowJar` 构建成功，待部署重启验证：`/sethome` 后确认 `home` 表生成。若重启后仍报表不存在，需检查启动日志中 `KaTpa 家位置数据库初始化失败` 一行的具体原因（重点排查 MySQL 用户是否缺 `CREATE` 权限，可手动 `GRANT ALL ON katpa.* TO 'katpa'@'%';` 或以管理员手动执行建表 SQL）。

**修复记录（2026-09-10）：** Hopper 漏斗窗口拒绝/接受按钮无响应（`/tpdeny` 命令正常）。根因：`RequestHopperMenu.respond()` 用 `p.performCommand("tpdeny " + requestId)` + `close()`，在库存关闭期间 `performCommand` 行为不可靠，命令可能未执行。修复：移除通用 `respond()` 方法，新增 `accept()`/`deny()` 私有方法，直接调用 `((KaTpaPlugin) plugin).requests().accept(p, request.id())` / `.deny(p, request.id())`，绕过命令解析层。改动文件：`RequestHopperMenu.java`（+`KaTpaPlugin` 导入，-`UUID` 导入，-`requestId` 局部变量，新增两个方法）。已 `shadowJar` 构建通过。

**修复记录（2026-09-12）：** 创建/保存玩家地标报 `保存玩家地标失败: (conn=xxx) Parameter at position 18 is not set`。根因：`PlayerWarpStore.save()` 的 `INSERT ... VALUES(?,...,?)` 共 18 列/占位符，但 `setString(5, server)` 之后**漏绑 `world`**，后续参数整体错位 1 位，末尾 `created_at`（第 18 位）始终未设置 → JDBC 抛 "Parameter at position 18 is not set"。修复：在 `server` 后补 `stmt.setString(6, warp.world())`，并把 x/y/z/yaw/pitch/description/icon_*/cost/cooldown/created_at 依次后移到 7–18。已 `./gradlew build` 通过（JDK 21，`JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64`）。

**修复记录（2026-09-12 续）：** `/setwarp` 报同样错误 `保存地标失败: Parameter at position 18 is not set`。根因：**`WarpStore.save()` 存在完全相同的漏绑**——列清单为 `id,name,server,world,x,...,created_at,updated_at`（18 列），但 `setString(3, server)` 后直接 `setDouble(4, x)`，漏绑 `world`，导致末位 `updated_at`（第 18 位）未设置。此前排查误判为"WarpStore 无此问题"（当时 grep 按 `setString(5, server)` 位置匹配，而 WarpStore 的 server 在第 3 位），已纠正。修复：补 `stmt.setString(4, warp.world())` 并将后续参数后移到 5–18。已重新 `./gradlew build` 通过。**教训：核对列与参数必须逐位对照，不能靠固定序号 grep；同批 SQL 代码易复制传播同一缺陷。** 复核其余 store：`HomeStore`（15/15，world 在第 5 位）、`BackStore`（last_location 9/9、death_location 10/10）、`PwarpMetaStore`/`WarpRatingStore` 均一一对应，无此问题。

**部署与验证（2026-09-12）：** 构建产物 `build/libs/KaTpa-1.2.0.jar`（SHA-256 `4dbc7971...c2aee009`）已复制到 7 个后端服务器；仅 Lobby 经 `mcsm restart lobby` 重启验证，KaTpa v1.2.0 正常加载、`/katap reload` 正常。另用共享 MySQL 以事务回滚方式验证：旧绑定精确复现 `Parameter at position 18 is not set`，新绑定 18 位成功。其余 6 服待重启生效。详见 `/home/Server/handoff/plugin-sync.md`。

> 附：日志 `语言文件已自动补全缺失键: pwarp-created` 为 INFO 级自愈行为（服务器磁盘上的旧 `lang/zh_CN.yml` 缺该键，`MessageService` 从 JAR 默认补写），非错误。

**修复记录（2026-09-12）：** pwarp 编辑 GUI 内「修改名称/描述/冷却/费用」按钮点击无反应。根因：`pwarp_editor.yml` 动作只传 3 段（`katpa: pwarp set desc {pwarp_name}`），而 `KaTpaGuiActions.handlePlayerWarpSet` 旧实现要求 `args.length >= 4`（把新值当作命令内联参数），条件不满足时**静默 return**，既不提示也不改值；对比管理员 warp 处理器 `handleWarp` 早已用 `ChatInputManager` 聊天捕获输入。修复：将 `handlePlayerWarpSet` 的 name/cost/cooldown/desc 四个分支统一改为 `chat.capture(...)` 提示输入（cancel 取消），设置后 `showPwarpEditor` 刷新；改名后回 `showPwarpManager`。改动文件：`KaTpaGuiActions.java:230`（`handlePlayerWarpSet`）。已 `./gradlew build` 通过。

**新增（2026-09-12）——吟唱前目标可用性校验：** 所有传送（tpa/back/dback/home/warp/pwarp）在开始吟唱前校验目标是否可达，失败则不吟唱并提示。
- KaProxy 侧：presence 包追加在线子服列表（`ProxyAdapter.servers()` + `pingServers()` 周期性 ping 探活，Velocity/Bungee 各自实现），只下发在线子服。
- KaTpa 侧：`CrossServerService` 解析并缓存服务器列表，新增 `isServerAvailable(name)`（列表未知时退化为仅要求代理连通，兼容旧版代理）；`TeleportService.ensureTargetAvailable(...)` 统一同服世界/跨服子服校验，接入 10 处本服+跨服分支与 `beginNetwork`。
- 新增语言键 `target-server-unavailable`、`target-world-unloaded`。详见 `handoff/services.md`、`handoff/network.md` 与 `/home/Plugins/KaProxy/handoff/presence.md`。

**修复记录（2026-09-12）：** 跨服 warp/pwarp/home 传送跳过吟唱直接切服（本服有 3s 吟唱）。根因：`HomeService`/`WarpService`/`PlayerWarpService` 的 `teleportCrossServer` 未走 `beginDirect`，直接 `backRequest`；`BackService`/`DbackService` 早已修正。修复：三者在校验后调用 `beginDirect(module, () -> backRequest(...))`，跨服吟唱与本服一致（`modules.<module>.warmup-seconds`）。`./gradlew build` 通过并已部署。

**新增（2026-09-12）——back 短距离传送忽略记录：** `PlayerListener.onTeleport` 新增 `isNegligibleTeleport(event)`：同世界且 `from.distanceSquared(to) < min-distance²` 时跳过 `back().recordLocation`，避免短距离插件传送覆盖 `/back` 位置。新增配置 `modules.back.min-distance`（默认 16，0=始终记录），`config-version` 提升到 2（`ConfigUpdater.CURRENT_CONFIG_VERSION=2`），旧配置启动时自动备份并合并。

## 关键约定

- 模块开关 `modules.<name>.enabled`（默认 true）控制服务创建与命令注册；关闭模块注册统一 `module-disabled` 提示。
- 存储层集中连接管理：`SettingsStore` 持有唯一 `JdbcConnectionManager`，其余 store 通过 callback 使用连接；管理器负责跨 Store 串行化、MySQL 失效检查和后续操作自动重连。
- 所有写操作经各 store 单线程池异步落盘。
- 消息与界面文本走 `lang/zh_CN.yml`，缺失键自动补全并写回磁盘。**列表菜单条目文本也已 i18n（2026-09-21）**：`gui.list.*`（各列表类型条目名称/描述）、`gui.prompt.*`（聊天输入提示）、`gui.request-menu.*`（请求漏斗窗口）、`chat-input.*`（取消/超时）；`menu-missing` 为菜单缺失提示。菜单按钮文本仍在 `gui/*.yml`。
## 文本/图标/外部物品能力（2026-09-18）

移植 KaMenu/KaMMOUpgrade 方案，新增 `org.katacr.katpa.text` 包：`TextParser`（legacy+MiniMessage 智能解析、`&item:[x]`→sprite、`applyName/applyLore`）、`AdventureCompatibility`（旧核心无 MiniMessage 时回退）、`BukkitItemMetaCompat`（反射设置 Component 名/lore、`createInventory(Component)`、`setItemModel`）、`AdventureSender`（组件消息/actionbar，旧核心回退 legacy）、`ItemSpriteReference`、`MinecraftFeatures`、`CraftEngineResourcePackSpriteResolver`、`MaterialUtils`、`ItemPropertyReader`、`ExternalItemSpriteManager`（`item_sprites.yml` 覆盖 + 缓存 + 重载钩子）；`util/` 增 `ExternalItemUtils`（ce/ia/oraxen）、`CraftEngineUtils`（重写）、`ItemStacks`。

接入点：`MessageService.component/sendComponent`、`GuiManager`（标题/`buildItem` 材质+名/lore/`translateItemColors`/`executeAction`）、`KaTpaGuiListProvider.buildIconItem`、`ItemBuilder`、`RequestHopperMenu`、`InventoryInteractionPlatform.sendActionBar`、`InventoryMenu.build` 标题；`KaTpaPlugin.onLoad` 用 Libby 挂载 adventure 4.26.1（`libraries/` 已有缓存），`onEnable` 初始化 sprite 管理器；`plugin.yml` softdepend 增 CraftEngine/ItemsAdder/Oraxen/PlaceholderAPI。

依赖：`build.gradle.kts` adventure 升到 4.26.1 并新增 minimessage/key/plain。资源新增 `item_sprites.yml`。docs(zh/en) `config/config.md` 已加「文本与图标格式」节。

验证：`./gradlew build` 通过；部署 Lobby 并重启，KaTpa 启用无异常、生成 `item_sprites.yml`、19 个 GUI 加载正常；`/katap reload`+`/katap help`（控制台）走新消息链路无异常。启动日志：`Adventure 来源=adventure-api-5.2.0.jar，MiniMessage=true，Sprite(1.21.9+)=true`（Paper 核心类优先于 Libby，说明 MiniMessage/字形/sprite 均可用）。**待玩家在线实测字形/图标显示。**

注意：低版本（<1.21.9 或无新版 Adventure）自动降级；`&item:` 在低版本会被移除。

## 2026-09-20 本批改动（1.2.1）

- **世界名显示 Multiverse 别名**：新增 `util/WorldNames.java`，反射调用 Multiverse-Core API（`MultiverseCoreApi.get().getWorldManager().getWorld(name).getAliasOrName()`），有别名用别名，未装/无别名回退 Bukkit 世界名。`plugin.yml` softdepend 增 `Multiverse-Core`。接入 warp/home 列表 lore 与 `{warp_world}`/`{home_world}`、`target-world-unloaded` 消息。
- **服务器显示别名 `server_id`**：三表含该列（见 `storage.md`），模型含 `serverId`+`displayServer()`；`CrossServerService.displayServerId()` 返回 `config.yml: server-id`。菜单/提示显示别名，跨服路由仍用真实 `server`。
- **列表三态槽位**：`display/actions`（已有）、`empty-display/empty-actions`（已解锁空槽）、`lock-display/lock-actions`（未解锁）；`GuiListItem.Kind`（NORMAL/EMPTY/LOCK/BLANK）；`modules.home.total-slots`/`modules.pwarp.total-slots`（默认 10）。
- **图标设置改可点击文本**：新增 `text/ClickableText.java`，用 Adventure `ClickEvent.callback`（Paper 内置回调，无指令二次确认），点按钮→关容器→发 `[此处]` 可点击提示→点后读手持物品设图标→重开编辑器。
- **家菜单合并**：删除 `home_manager.yml`，`/home` 与 `/sethome` 无参数均打开 `home_selector`（含新建 A）。
- **语言节点清理**：删除废弃 `ui.*`（保留 `ui.usage.*`）及 `receiver-busy`/`sneak-hint`/`database-error`/`warp-location-updated`/`pwarp-updated`/`module-*`；补齐缺失的 `*-icon-*`/`*-rename-*`/`invalid-number` 等键。
- **Dialog 措辞清理**：注释/日志由「Dialog」改为「库存菜单」；`AcceptMode.DIALOG` 枚举与其别名保留。
- **部署**：1.2.1 已部署 Lobby 验证（含三表 `server_id` 列迁移成功）；其余 6 台待同步。

- **修复 ConfigUpdater 配置升级丢值（严重）**：`ConfigUpdater.collectValues` 递归用绝对路径 `section.get(path)` 取值，MemorySection 返回 null，导致嵌套配置叶子全部提取失败、升级时用户值被默认值覆盖（只保留 2 项）。改为相对路径 `section.get(key)` 后保留 80 项。详见 `config.md`。全部 7 台后端已从各自 `config_v2_backup_*.yml` 恢复并重新升级。
- **右键床自动设家**：`PlayerListener.onBedInteract`（`PlayerInteractEvent` RIGHT_CLICK_BLOCK + `_BED` + 主手）→ `HomeService.setBedHome`，白天夜晚均可；内存 `lastBedHomes` 按「床头格 key」去重，重复右键同一张床不更新。配置 `modules.home.bed-home`/`bed-home-name`。详见 `services.md`。
- **家编辑器**：`gui/home_editor.yml`（图标/更新位置/删除）；`home_selector`/`home_manager` 列表右键改为 `katpa: home edit`。新增 `HomeService.updateLocation`、`InteractionPlatform.showHomeEditor`。
- **列表空槽位自定义**：列表按钮新增平级键 `empty-display`/`empty-actions`（结构与 `display`/`actions` 一致），作用于未使用槽位。详见 `ui.md`。
- **按钮级 permission**：`GuiButton.permission` + `GuiManager.canView`。
- **pwarp 删除入口**：`pwarp_editor.yml` 的 `R`（本人删除）与 `Q`（管理员强行删除，`katpa.pwarp.admin` 可见）；`pwarp_selector` 列表项右键进编辑器。
- **部署**：1.2.1 已部署 Lobby 并重启验证；其余 6 台已于 2026-09-20 恢复配置并部署 1.2.1（详见 `/home/Server/handoff/plugin-sync.md`）。

## 2026-09-21 翻页与 open 变量继承修复

- **`{page}/{total_pages}` 首次渲染未替换**：`GuiManager.renderMenu` 原先在渲染循环之后才 `session.set("page"/"total_pages")`，而翻页按钮（非列表按钮）在循环内先渲染，变量尚未写入，标题/名称里原样显示 `{page}`。修复：渲染前预计算 `maxPages` 并先写入会话，再渲染所有按钮（`GuiManager.java:280`）。
- **首/末页点击仍重算**：`KaTpaGuiActions.handlePage` 改为先算目标页，`target == current` 时直接 return，不 reopen、不重算。
- **`open:` 动作不继承会话变量（本次用户报告）**：`pwarp_owner_list.yml` 标题 `{owner_name}` 未解析。根因：`GuiManager.executeAction` 的 `open:` 分支调用 `openMenu(player, target)`，只传菜单 id 与 `:` 后 args，**丢弃当前会话全部变量**；而 `owner_name` 仅存在于被点击列表项的 item 变量（`pwarp_players` 的 `{owner_uuid}`/`{owner_name}`）中，未进入新菜单会话。修复：`open:` 分支改为复制当前 `session.variables()` 作为 `initialVariables`，解析 `menuId:args` 后 `openMenu(player, menuId, args, inherited)`（`GuiManager.java:564`）。所有 `open:` 跳转（settings/relation_editor/pwarp_*）现均继承来源菜单变量。
- **部署**：已构建并部署 Lobby 重启验证（`cdd8a443…`）。其余 6 台待用户验证后同步。

## 2026-09-21 第二批：/pw 快捷传送、管理员指令、排行榜缓存、PlayerPoints 收费

- **新增 `/pw <名称>` 快捷传送指令**：`command/PwCommand.java`，无参打开排行榜，按名传送并补全地标名；`plugin.yml` 新增 `pw` 指令（`katpa.pwarp.use`）。`/pwarp` 无参与 `/pw` 无参均改为打开排行榜（原 `pwarp_selector`）。
- **`/pwarp admin <edit|delete|reload>`**：`PlayerWarpCommand.handleAdmin`，需 `katpa.pwarp.admin`；`edit` 打开任意地标编辑器，`delete` 强行删除，`reload` 调用 `PlayerWarpService.reloadData()`（仅重载 player_warp 数据 + 重算排行榜缓存，不动配置/语言/菜单）。新增语言键 `pwarp-admin-command-usage`/`pwarp-admin-edit-usage`/`pwarp-admin-delete-usage`/`pwarp-admin-reloaded`。
- **排行榜缓存**：`WarpRatingStore` 新增 `LeaderboardEntry(warpId, stars, score)` 与前 N 名缓存（`leaderboard-cache-size` 默认 30）+ `leaderboardIndex`；`leaderboard(int)` 改为读缓存，新增 `cachedEntry`/`refreshLeaderboard(bool)`。`computeLeaderboard` 用**权重得分**（`scoreOf`）排序（此前 `SUM(stars)` 是按原始星数而非加权分，与展示不一致，已修正）。触发点：评分后 `rate()` 重算；创建/删除通过 `PlayerWarpStore.markLeaderboardDirty()` 在写库成功后重算（避免与写事务竞争）；跨服经 `warp_rating` 主题广播（KaProxy `DATA_SYNC_TOPICS` 增该主题）。
- **`KaTpaGuiListProvider.buildPwarpLeaderboard`** 改用缓存（`leaderboard(0)`）渲染，不再逐条 `averageStars`/`totalScore` 查库。
- **PlayerPoints 可选前置 + 创建收费**：新增 `util/PointsHook.java`（反射，未装安全降级），`KaTpaPlugin.points()`，`plugin.yml` softdepend 增 `PlayerPoints`。`config.yml` 新增 `modules.pwarp.create-cost.{currency,money,points}`（`money`=Vault 金币 / `points`=PlayerPoints 点券，二选一），`PlayerWarpService.chargeCreateCost` 仅在**新建**时扣费（改名/改描述等编辑不收费）。配置版本 `CURRENT_CONFIG_VERSION` 4→5。
- **收费二次确认**：`/pw <名称>`（及菜单 `katpa: pwarp warp`）对非创建者的收费地标先发可点击消息 `pwarp-cost-confirm`（`[确定]`/`[取消]`，30s 失效）。`ClickableText` 新增多区域 `buildMulti`/`sendClickableMulti`（按 `[ ]` 文字分派回调），原单区域 `sendClickable` 保留。`PlayerWarpService.warp(player,name,confirmed)` 重载。
- **菜单统一交互**：玩家地标列表（`pwarp_selector`/`owner_list`/`favorites`/`history`/`leaderboard`）条目统一 **左键传送 / 右键评分 / Q 收藏**；`pwarp_manager`（我的地标）保留左/右键编辑；`warp_*`（服务器地标）不涉及。**Lobby 的 `gui/` 为玩家自定义版本（CE 材质/自定义布局），改动前已逐一比对并就地编辑，未覆盖。**
- **部署**：jar `873b15b3…` 已部署 Lobby 重启验证（配置升级 v4→v5 保留 83 项，PlayerPoints 挂载成功，18 菜单加载正常）；KaProxy 亦已构建（`warp_rating` 主题）。其余 6 台待用户验证后同步。

## 2026-09-21 第三批：pwarp 全局冷却、移除迁移代码、编辑器更新位置

- **pwarp 传送改全局冷却（按玩家）**：删除每个地标自己的冷却（`PlayerWarp.cooldownSeconds` 字段、`player_warp.cooldown_seconds` 列、`PlayerWarpService.setCooldown`、`/katap pwarp set cooldown` 与补全、`KaTpaGuiActions` 的 cooldown 分支、`pwarp_cooldown` 菜单变量、编辑器 C 按钮）。改为 `modules.pwarp.cooldown-seconds`（默认 30，可配，0 不冷却）的**全局按玩家**冷却：`PlayerWarpService.cooldowns` 改为 `Map<UUID, Long>`，`cooldownRemaining(player)`/`startCooldown(player)` 与具体地标无关，冷却期间该玩家不能再次 `/pwarp`/`/pw` 传送（换地标也一样）。配置版本 v5→v6。
- **移除旧库迁移代码**：删除 `storage/ColumnMigration.java` 及三处 `ensureColumn` 调用（`server_id`/`world_alias` 直接写在 `CREATE TABLE` 中）。理由：插件未发布，无需兼容旧库。
- **删除旧表**：`DROP TABLE katpa.player_warp`（数据未发布），插件启动时按新 schema 重建（无 `cooldown_seconds`）。
- **pwarp 编辑器新增「更新位置」**：`pwarp_editor.yml` 的 C（冷却）改为 U（`ENDER_PEARL`，`katpa: pwarp update {pwarp_name}`）；`KaTpaGuiActions.handlePlayerWarp` 新增 `update` 分支；`PlayerWarpService.updateLocation` 以玩家当前位置覆盖坐标/世界/服务器并保留描述/图标/费用；builder 新增 `server/serverId/world/worldAlias/position` 链式方法；语言键 `pwarp-location-updated`。
- **Lobby `gui/pwarp_editor.yml` 已就地编辑**（未覆盖玩家自定义内容）。
- **部署**：jar `3ad54bca…` 已部署 Lobby 重启验证（配置升级 v5→v6 保留 86 项，PlayerPoints 挂载，18 菜单加载，`player_warp` 表重建为无 `cooldown_seconds`）。其余 6 台待同步。

## 2026-09-21 第四批：列表文本 i18n、评分交互、创建者禁止评分

- **列表条目文本全部 i18n**：`KaTpaGuiListProvider` 原先在 Java 内硬编码列表条目名称/lore（约 56 处中文），现全部改为 `plugin.messages().text("gui.list.*", vars)`。新增键：`gui.list.request.*`/`online.*`/`warp.*`/`pwarp.*`/`pwarp-owner.*`/`home.*`/`relation.*`。
- **聊天输入提示 i18n**：`KaTpaGuiActions` 的 `chat.capture` 提示改 `gui.prompt.*`；`ChatInputManager` 的取消/超时改 `chat-input.*`；`RequestHopperMenu`（请求漏斗窗口）改 `gui.request-menu.*`；`InventoryInteractionPlatform` 的 `list_type_display` 改 `list.*`；`GuiManager` 菜单缺失提示改 `menu-missing`。
- **移除文本依赖逻辑**：`ClickableText.HandlerResolver` 由「按区域文字」改为「按区域序号 `(index, label)`」分派；`PlayerWarpService.sendCostConfirm` 不再用 `label.contains("确定"/"取消")` 判断（该写法在语言文本被翻译/修改后会失效），改为 `index == 0`/`index == 1`。
- **评分界面改造**：`pwarp_rate.yml` 的 5 星改为单个列表按钮 `type: PWARP_STARS`（占 5 槽），新增平级键 `lit-display`（点亮）/`unlit-display`（未点亮）；`GuiListItem.Kind` 新增 `LIT`/`UNLIT`；点 N 星左侧 N 个点亮、右侧未点亮；新增提交按钮 `S`（`katpa: pwarp do rate {pwarp_name} {pwarp_rate_selected}`），不再点击即提交；`KaTpaGuiActions` 新增 `katpa: pwarp select <n>`；`InventoryInteractionPlatform.showPwarpRate` 以玩家已有评分作为初始选择；`GuiManager.buildPlaceholderItem` 支持条目级变量（`{pwarp_star}`）。
- **创建者禁止评分**：`PlayerWarpService.rate` 与菜单 `katpa: pwarp rate` 入口均拦截创建者自评，提示新键 `pwarp-rate-own`；排行榜/列表程序化 lore 对创建者不再显示「右键评分」。
- **空列表不显示占位屏障**：`GuiManager.renderMenu` 列表无数据时不再插入 BARRIER 占位物品，所有列表槽位登记为 `BLANK`（不渲染、点击无动作）；删除 `emptyPlaceholder`/`renderPlaceholderSlot`；移除全部 `gui/*.yml` 的 `empty_text` 键（26 处，含 Lobby 自定义菜单）。
- **排行榜条目补 Q 键收藏 lore**：`buildPwarpLeaderboard` 程序化 lore 增加 `[Q键] 收藏/取消收藏`。
- **Lobby `lang/zh_CN.yml` 同步**：补齐 249 键（原磁盘 179 键），并修正 4 处陈旧值（`pwarp-command-usage`/`pwarp-set-usage`/`help.pwarp`/`help.pwarpedit`）。
- **部署**：jar `90d0046f…` 已部署 Lobby 重启验证（18 菜单加载，无缺失语言键日志）。其余 6 台待同步。

## 2026-09-21 第五批：各模块世界黑名单

- **背景**：原 `modules.tpa.disabled-worlds` 仅作用于 TPA 请求；back/dback/home/warp/pwarp 不查黑名单，副本世界若仍加载则 `/back`、`/dback` 仍可进入。
- **新增配置**：`modules.back.disabled-worlds`、`modules.dback.disabled-worlds`、`modules.warp.disabled-worlds`、`modules.home.disabled-worlds`、`modules.pwarp.disabled-worlds`（均为世界名列表，区分大小写）。配置版本 v6→v7。
- **`TeleportService`**：`isDisabledWorld` 抽为公开 `isWorldDisabled(module, world)`；新增 `isCurrentWorldDisabled(module, player)`；`ensureTargetAvailable` 新增带 `module` 的重载，先校验目标世界黑名单（提示新键 `target-world-disabled`），再走原有代理/世界加载校验。原无 module 的重载委托之。
- **接入点**：`BackService`/`DbackService`/`HomeService`/`WarpService`/`PlayerWarpService` 的本服与跨服 `ensureTargetAvailable` 全部传 module。
- **阻止记录**：`BackService.recordLocation` 与 `DbackService.recordDeath` 在玩家当前世界命中黑名单时直接返回，不记录。
- **阻止创建**：`HomeService.setHome`/`updateLocation`、`WarpService.setWarp`、`PlayerWarpService.create`/`updateLocation` 在当前世界命中黑名单时拒绝，提示新键 `world-creation-disabled`。
- **新增语言键**：`target-world-disabled`、`world-creation-disabled`。
- **部署**：jar `240a9185…` 已部署 Lobby 重启验证（配置升级 v6→v7 保留 87 项，6 处 `disabled-worlds` 就位）。其余 6 台待同步。

## 2026-09-21 第六批：跨服 TPA 世界黑名单提前拒绝

- **问题**：跨服 TPA 源服无法预知目的地世界，原先只在落点阶段（`arriveNetwork`→`canUse`）校验黑名单，导致旅行者已被切到目标服才失败、滞留目标服。
- **修复**：`RequestService.rejectIfReceiverWorldDisabled(receiver, request)` —— 接收者当前世界命中 `modules.tpa.disabled-worlds` 时，在**接受环节**拒绝：本地请求直接拒绝并通知发送者 `target-world-disabled`，跨服请求 `network.deny` 后 `remove`；`accept()` 与 `receiveNetwork()` 白名单自动接受分支均接入。`/tpa`（目的地=接收者）与 `/tpahere`（旅行者=接收者）皆覆盖。落点阶段校验保留兜底。
- **部署**：jar `07a474f3…` 已部署 Lobby（重启时 mcsm 状态卡住，已用 stop 触发重新启动恢复）。其余 6 台待同步。
