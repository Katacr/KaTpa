# KaTpa 交接文件索引

> 项目：KaTpa —— 面向 Paper 1.21.7 / Spigot 1.21.6+、JDK 21 的玩家传送插件
> 最后更新：2026-09-07（warp/home 图标与描述扩展，item_model 反射方案，/katpa warp 管理指令，中英文文档同步）

## 项目概览

- **技术栈**：Gradle（Kotlin DSL）+ ShadowJar，单 JAR 同时编译 `src/main`（Paper 适配器）与 `src/spigot`（Spigot 适配器）两个 sourceSet。
- **运行平台**：启动时通过 `Class.forName` 反射探测 `io.papermc.paper.dialog.Dialog` 或 `net.md_5.bungee.api.dialog.Dialog`，自动选择 Paper 原生 Dialog 或 Spigot Bungee Dialog 实现，单 JAR 跨平台启动。
- **依赖下载**：仅内置 Libby（`net.byteflux:libby-bukkit`），首次开服按 `storage.type`（sqlite/mysql）下载 SQLite JDBC 或 MariaDB JDBC 到服务器 `libraries/` 目录。
- **当前版本**：`1.1.0`（`build.gradle.kts:8`）。
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

## 关键约定

- 模块开关 `modules.<name>.enabled`（默认 true）控制服务创建与命令注册；关闭模块注册统一 `module-disabled` 提示。
- 存储层单连接模式：`SettingsStore` 持有唯一物理 `Connection`，其余 store 共享。
- 所有写操作经各 store 单线程池异步落盘。
- 消息与界面文本走 `lang/zh_CN.yml`，缺失键自动补全并写回磁盘。
