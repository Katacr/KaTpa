# KaTpa 交接文件索引

> 项目：KaTpa —— 面向 Paper 1.21.7 / Spigot 1.21.6+、JDK 21 的玩家传送插件
> 最后更新：2026-09-16（数据库连接失效检测、自动重连与跨 Store 串行化）

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
- `model/PlayerWarp.java`：record（id/ownerId/ownerName/name/server/world/x/y/z/yaw/pitch/description/icon*/cost/cooldownSeconds/createdAt）。
- `storage/PlayerWarpStore.java`：独立表 `player_warp` + owner 二级索引 + `byOwner`/`count`/`find(ownerId,name)`。
- `storage/WarpRatingStore.java`：评分表 `player_warp_rating`（PK player_uuid+warp_id）、离线收入表 `player_warp_pending_income`、`scoreOf` 权重（5★+10/4★+5/3★+1/2★-5/1★-10）、`leaderboard(limit)`、`addPendingIncome`/`takePendingIncome`。
- `service/PlayerWarpService.java`：创建（数量上限 `katpa.pwarp.amount.<n>`）/传送（创建者免 cost）/编辑/评分/收入结算（在线 deposit，离线挂账）。
- `command/PlayerWarpCommand.java`：`/pwarp` 传送 + 列表。
- `resources/gui/`：`pwarp_selector`/`pwarp_manager`/`pwarp_editor`/`pwarp_rate`/`pwarp_leaderboard`（新增 PWARP_LIST / PWARP_LEADERBOARD 列表类型）。
- 扩展：`KaTpaCommand`（handlePlayerWarp）、`KaTpaGuiListProvider`（buildPlayerWarps/buildPwarpLeaderboard）、`KaTpaGuiActions`（handlePlayerWarp/do rate）、`InteractionPlatform`/`InteractionService`/`InventoryInteractionPlatform`（showPwarp*）、`PlayerListener.onJoin`（领取离线收入）、`KaTpaPlugin`（模块初始化/访问器/命令注册/disable 关闭 store）。
- `plugin.yml`：`pwarp` 命令 + 权限 `katpa.pwarp.use`/`create`/`admin`；`katpa.pwarp.amount.<n>` 动态权限。
- `config.yml`：`modules.pwarp.*`（enabled/default-amount/default-cost/default-cooldown/name-max-length/description-max-length）。
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
- 消息与界面文本走 `lang/zh_CN.yml`，缺失键自动补全并写回磁盘。
