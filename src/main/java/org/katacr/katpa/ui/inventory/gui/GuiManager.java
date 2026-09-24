package org.katacr.katpa.ui.inventory.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 gui/ 资源目录加载 YAML 菜单并构建 Bukkit 库存。
 *
 * 语法沿用 Ka 系列 Container 风格：{@code title}/{@code Layout}/{@code button}，
 * 每个按钮含 {@code display}（物品）与 {@code actions}（点击类型 → 动作列表）。
 * 动作以字符串列表表示，前缀决定执行方式（{@code open}/{@code close}/{@code tell}/
 * {@code actionbar}/{@code command}/{@code console}/{@code katpa}）。
 */
public final class GuiManager {
    private final JavaPlugin plugin;
    private final Map<String, GuiMenu> menuCache = new ConcurrentHashMap<>();
    private final Map<String, GuiActionHandler> actionHandlers = new ConcurrentHashMap<>();
    private final Map<String, GuiListProvider> listProviders = new ConcurrentHashMap<>();
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, Map<String, String>>> listContexts = new ConcurrentHashMap<>();
    /** 列表按钮各槽位的条目类型（EMPTY/LOCK），用于点击时选择 empty-actions/lock-actions。 */
    private final Map<UUID, Map<Integer, GuiListItem.Kind>> listSlotKinds = new ConcurrentHashMap<>();
    private final Map<UUID, GuiMenuHolder> holders = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> refreshTasks = new ConcurrentHashMap<>();

    /** 创建绑定插件的 GUI 管理器。 */
    public GuiManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 注册一个命名空间动作处理器，处理 {@code katpa:namespace} 形式的动作。 */
    public void registerActionHandler(String namespace, GuiActionHandler handler) {
        actionHandlers.put(namespace.toLowerCase(), handler);
    }

    /** 注册列表数据提供器，处理菜单中 {@code type: '<列表类型>'} 的按钮填充。 */
    public void registerListProvider(GuiListProvider provider) {
        listProviders.put("__global__", provider);
    }

    /** 重载所有菜单 YAML（从磁盘，仅补齐 JAR 中磁盘缺失的菜单，不覆盖已有文件）。 */
    public void reload() {
        extractDefaultMenus();
        menuCache.clear();
        File dir = new File(plugin.getDataFolder(), "gui");
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            menuCache.put(id, GuiMenu.parse(config));
        }
        plugin.getLogger().info("已加载 " + menuCache.size() + " 个 GUI 菜单。");
    }

    /** 打开指定菜单；支持带参数（如 {@code relation_editor:whitelist}）。 */
    public void openMenu(Player player, String menuIdWithArgs) {
        String[] parts = menuIdWithArgs.split(":", 2);
        String menuId = parts[0];
        String args = parts.length > 1 ? parts[1] : "";
        openMenu(player, menuId, args);
    }

    /** 打开指定菜单并传入参数字符串。 */
    public void openMenu(Player player, String menuId, String args) {
        openMenu(player, menuId, args, new HashMap<>());
    }

    /** 打开指定菜单并传入参数与初始变量（供业务层预填展示变量）。 */
    public void openMenu(Player player, String menuId, String args, Map<String, String> initialVariables) {
        GuiMenu menu = menuCache.get(menuId);
        if (menu == null) {
            String message = ((org.katacr.katpa.KaTpaPlugin) plugin).messages()
                    .text("menu-missing", Map.of("menu", menuId));
            player.sendMessage(ChatColor.RED + message);
            return;
        }
        Map<String, String> vars = new HashMap<>(initialVariables);
        vars.put("args", args == null ? "" : args);
        MenuSession session = new MenuSession(menuId, args, vars);
        sessions.put(player.getUniqueId(), session);
        renderMenu(player, menu, session);
    }

    /** 处理库存点击；由 InventoryMenuListener 路由到本方法。 */
    public void handleClick(Player player, int slot, String clickType) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        GuiMenu menu = menuCache.get(session.menuId());
        if (menu == null) {
            return;
        }
        String ch = menu.charAt(slot);
        if (ch == null) {
            return;
        }
        GuiMenu.GuiButton button = menu.buttons().get(ch);
        if (button == null) {
            return;
        }
        if (!canView(player, button)) {
            return;
        }
        if (isListButton(button)) {
            Map<Integer, GuiListItem.Kind> kinds = listSlotKinds.get(player.getUniqueId());
            GuiListItem.Kind kind = kinds == null ? null : kinds.get(slot);
            Map<String, String> itemVars = listContexts.getOrDefault(player.getUniqueId(), new HashMap<>()).get(slot);
            if (itemVars != null) {
                session.variables().putAll(itemVars);
            }
            if (kind == GuiListItem.Kind.BLANK) {
                return;
            }
            if (kind == GuiListItem.Kind.EMPTY) {
                for (String action : button.emptyActionsFor(clickType)) {
                    executeAction(player, action, session);
                }
                return;
            }
            if (kind == GuiListItem.Kind.LOCK) {
                for (String action : button.lockActionsFor(clickType)) {
                    executeAction(player, action, session);
                }
                return;
            }
        }
        List<String> actions = button.actionsFor(clickType);
        for (String action : actions) {
            executeAction(player, action, session);
        }
    }

    /** 玩家关闭菜单时清理会话、刷新任务与 holder 引用。 */
    public void handleClose(Player player) {
        sessions.remove(player.getUniqueId());
        listContexts.remove(player.getUniqueId());
        listSlotKinds.remove(player.getUniqueId());
        holders.remove(player.getUniqueId());
        org.bukkit.scheduler.BukkitTask task = refreshTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    /** 以当前会话状态（含已修改的 page）重新渲染并打开菜单（翻页需重开以更新标题变量）。 */
    public void reopen(Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        GuiMenu menu = menuCache.get(session.menuId());
        if (menu == null) {
            return;
        }
        // 翻页重开需先解除 holder 绑定，强制 renderMenu 走新建路径
        holders.remove(player.getUniqueId());
        renderMenu(player, menu, session);
    }

    /** 判断按钮是否对玩家可见（未配置 permission 时恒可见）。 */
    private boolean canView(Player player, GuiMenu.GuiButton button) {
        String permission = button.permission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    /** 判断按钮是否为列表型（由业务层提供条目数据）。 */
    private boolean isListButton(GuiMenu.GuiButton button) {
        return button.type() != null && !button.type().isBlank();
    }

    /** 将 JAR 内置 gui/ YAML 补齐到磁盘（仅当磁盘文件不存在时写入，不覆盖已有文件）。 */
    private void extractDefaultMenus() {
        File dir = new File(plugin.getDataFolder(), "gui");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建 gui 目录: " + dir.getAbsolutePath());
            return;
        }
        for (String name : listBundledMenus()) {
            plugin.saveResource("gui/" + name, false);
        }
    }

    /** 列出 JAR 内置 gui/ 目录下的 .yml 文件名。 */
    private List<String> listBundledMenus() {
        List<String> result = new java.util.ArrayList<>();
        try (InputStream stream = plugin.getClass().getClassLoader().getResourceAsStream("gui")) {
            if (stream == null) {
                return result;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            } catch (Exception ignored) {
            }
        } catch (IOException ignored) {
        }
        java.net.URL url = plugin.getClass().getClassLoader().getResource("gui");
        if (url == null) {
            return result;
        }
        if ("jar".equals(url.getProtocol())) {
            try {
                java.util.jar.JarFile jar = ((java.net.JarURLConnection) url.openConnection()).getJarFile();
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("gui/") && name.endsWith(".yml") && !name.equals("gui/")) {
                        result.add(name.substring("gui/".length()));
                    }
                }
            } catch (IOException ignored) {
            }
        } else if ("file".equals(url.getProtocol())) {
            File dir = new File(url.getFile());
            File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    result.add(f.getName());
                }
            }
        }
        return result;
    }

    /** 渲染菜单库存并打开给玩家；若玩家已持有同一菜单则原地更新不重开。 */
    private void renderMenu(Player player, GuiMenu menu, MenuSession session) {
        net.kyori.adventure.text.Component titleComponent = org.katacr.katpa.text.TextParser.parse(resolveText(player, menu.title(), session));
        int rows = menu.layout().size();
        int size = Math.max(9, Math.min(54, rows * 9));

        // 判断是否可原地更新（玩家当前打开的正是本菜单库存）
        GuiMenuHolder existing = holders.get(player.getUniqueId());
        Inventory inv;
        boolean inplace = false;
        if (existing != null && existing.getInventory() != null
                && player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory() == existing.getInventory()
                && existing.menu() == menu) {
            inv = existing.getInventory();
            inv.clear();
            inplace = true;
        } else {
            existing = new GuiMenuHolder(menu, session);
            inv = org.katacr.katpa.text.BukkitItemMetaCompat.createInventoryComponent(existing, size, titleComponent);
            if (inv == null) {
                inv = Bukkit.createInventory(existing, size, org.katacr.katpa.text.TextParser.toLegacy(titleComponent));
            }
            existing.bind(inv);
            holders.put(player.getUniqueId(), existing);
        }

        Map<Integer, Map<String, String>> slotContexts = new HashMap<>();
        GuiListProvider provider = listProviders.get("__global__");

        // 预计算总页数并写入会话，确保非列表按钮（翻页按钮）渲染时 {page}/{total_pages} 已就绪。
        int maxPages = 1;
        if (provider != null) {
            BukkitPlayer bp = new BukkitPlayer(player);
            Map<String, Integer> perPageByChar = new HashMap<>();
            for (String line : menu.layout()) {
                for (int c = 0; c < line.length() && c < 9; c++) {
                    String ch = String.valueOf(line.charAt(c));
                    if (" ".equals(ch)) {
                        continue;
                    }
                    GuiMenu.GuiButton b = menu.buttons().get(ch);
                    if (b != null && isListButton(b)) {
                        perPageByChar.merge(ch, 1, Integer::sum);
                    }
                }
            }
            for (Map.Entry<String, Integer> e : perPageByChar.entrySet()) {
                GuiMenu.GuiButton b = menu.buttons().get(e.getKey());
                if (e.getValue() > 0) {
                    int pages = (int) Math.ceil((double) Math.max(totalCount(bp, b.type(), session), 1) / e.getValue());
                    maxPages = Math.max(maxPages, pages);
                }
            }
        }
        session.set("page", String.valueOf(session.page() + 1));
        session.set("total_pages", String.valueOf(maxPages));

        // 收集列表型按钮的字符与槽位
        Map<String, List<Integer>> listSlotsByChar = new HashMap<>();
        for (int r = 0; r < rows; r++) {
            String line = menu.layout().get(r);
            for (int c = 0; c < line.length() && c < 9; c++) {
                String ch = String.valueOf(line.charAt(c));
                if (" ".equals(ch)) {
                    continue;
                }
                GuiMenu.GuiButton button = menu.buttons().get(ch);
                if (button == null) {
                    continue;
                }
                if (!canView(player, button)) {
                    continue;
                }
                int slot = r * 9 + c;
                if (isListButton(button)) {
                    listSlotsByChar.computeIfAbsent(ch, k -> new java.util.ArrayList<>()).add(slot);
                    inv.setItem(slot, null);
                } else {
                    ItemStack item = buildItem(player, button.display(), session);
                    if (item != null) {
                        inv.setItem(slot, item);
                    }
                }
            }
        }

        // 填充列表型按钮
        Map<Integer, GuiListItem.Kind> slotKinds = new HashMap<>();
        if (provider != null) {
            BukkitPlayer bp = new BukkitPlayer(player);
            for (Map.Entry<String, List<Integer>> entry : listSlotsByChar.entrySet()) {
                GuiMenu.GuiButton button = menu.buttons().get(entry.getKey());
                int perPage = entry.getValue().size();
                List<GuiListItem> items = provider.provide(bp, button.type(), session, session.page(), perPage);
                List<Integer> slots = entry.getValue();
                if (items.isEmpty()) {
                    // 无列表数据时不渲染任何占位物品，且点击不执行任何动作。
                    for (int slot : slots) {
                        slotKinds.put(slot, GuiListItem.Kind.BLANK);
                    }
                    continue;
                }
                for (int i = 0; i < slots.size() && i < items.size(); i++) {
                    int slot = slots.get(i);
                    GuiListItem item = items.get(i);
                    GuiListItem.Kind kind = item.kind() == null ? GuiListItem.Kind.NORMAL : item.kind();
                    ItemStack rendered = item.item();
                    if (kind == GuiListItem.Kind.EMPTY) {
                        rendered = buildPlaceholderItem(player, session, button.emptyDisplay());
                    } else if (kind == GuiListItem.Kind.LOCK) {
                        rendered = buildPlaceholderItem(player, session, button.lockDisplay());
                    } else if (kind == GuiListItem.Kind.LIT) {
                        rendered = buildPlaceholderItem(player, session, button.litDisplay(), item.variables());
                    } else if (kind == GuiListItem.Kind.UNLIT) {
                        rendered = buildPlaceholderItem(player, session, button.unlitDisplay(), item.variables());
                    }
                    if (rendered != null) {
                        inv.setItem(slot, translateItemColors(rendered));
                    }
                    if (item.variables() != null && !item.variables().isEmpty()) {
                        slotContexts.put(slot, item.variables());
                    }
                    if (kind != GuiListItem.Kind.NORMAL) {
                        slotKinds.put(slot, kind);
                    }
                }
                // 列表条目不足的剩余槽位按“空白填充”处理：不渲染、点击不执行任何动作。
                for (int i = items.size(); i < slots.size(); i++) {
                    slotKinds.put(slots.get(i), GuiListItem.Kind.BLANK);
                }
            }
        }

        if (!inplace) {
            scheduleRefresh(player, menu, session);
            player.openInventory(inv);
            // openInventory 触发 close 事件会清空会话，打开后重新注册
            sessions.put(player.getUniqueId(), session);
            listContexts.put(player.getUniqueId(), slotContexts);
            listSlotKinds.put(player.getUniqueId(), slotKinds);
        } else {
            // 原地更新不触发 close，直接刷新会话与上下文
            sessions.put(player.getUniqueId(), session);
            listContexts.put(player.getUniqueId(), slotContexts);
            listSlotKinds.put(player.getUniqueId(), slotKinds);
        }
    }

    /** 按给定 display 配置构建占位物品（颜色码翻译），未配置返回 null。 */
    private ItemStack buildPlaceholderItem(Player player, MenuSession session, ConfigurationSection display) {
        return buildPlaceholderItem(player, session, display, null);
    }

    /**
     * 按给定 display 配置构建占位物品（颜色码翻译），未配置返回 null。
     *
     * <p>{@code extraVariables} 为条目级变量（如 {@code {pwarp_star}}），临时并入会话供文本替换。
     */
    private ItemStack buildPlaceholderItem(Player player, MenuSession session, ConfigurationSection display,
                                           Map<String, String> extraVariables) {
        if (display == null) {
            return null;
        }
        ItemStack item;
        if (extraVariables == null || extraVariables.isEmpty()) {
            item = buildItem(player, display, session);
        } else {
            Map<String, String> previous = new HashMap<>();
            for (Map.Entry<String, String> entry : extraVariables.entrySet()) {
                previous.put(entry.getKey(), session.variables().put(entry.getKey(), entry.getValue()));
            }
            item = buildItem(player, display, session);
            for (Map.Entry<String, String> entry : previous.entrySet()) {
                if (entry.getValue() == null) {
                    session.variables().remove(entry.getKey());
                } else {
                    session.variables().put(entry.getKey(), entry.getValue());
                }
            }
        }
        return item == null ? null : translateItemColors(item);
    }

    /** 翻译列表条目物品的名称与描述颜色代码（克隆后处理，避免修改提供器内部对象）。 */
    private ItemStack translateItemColors(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        boolean changed = false;
        if (meta.hasDisplayName()) {
            org.katacr.katpa.text.TextParser.applyName(meta, meta.getDisplayName());
            changed = true;
        }
        if (meta.hasLore()) {
            org.katacr.katpa.text.TextParser.applyLore(meta, meta.getLore());
            changed = true;
        }
        if (!changed) {
            return stack;
        }
        ItemStack copy = stack.clone();
        copy.setItemMeta(meta);
        return copy;
    }

    /** 按菜单根 {@code update} 键设置的周期（tick）定时重渲染；无该键则不刷新。 */
    private void scheduleRefresh(Player player, GuiMenu menu, MenuSession session) {
        UUID id = player.getUniqueId();
        org.bukkit.scheduler.BukkitTask old = refreshTasks.remove(id);
        if (old != null) {
            old.cancel();
        }
        long period = menu.root() == null ? 0 : menu.root().getLong("update", 0L);
        if (period <= 0) {
            return;
        }
        refreshTasks.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            MenuSession current = sessions.get(id);
            if (current == null || !current.menuId().equals(session.menuId())) {
                org.bukkit.scheduler.BukkitTask task = refreshTasks.remove(id);
                if (task != null) {
                    task.cancel();
                }
                return;
            }
            renderMenu(player, menuCache.get(current.menuId()), current);
        }, period, period));
    }

    /** 询问提供器某列表类型的总条目数（用于计算总页数）。 */
    private int totalCount(BukkitPlayer player, String type, MenuSession session) {
        GuiListProvider provider = listProviders.get("__global__");
        if (provider == null) {
            return 0;
        }
        return provider.provide(player, type, session, 0, Integer.MAX_VALUE).size();
    }

    /** 根据 display 配置构建物品。 */
    private ItemStack buildItem(Player player, ConfigurationSection display, MenuSession session) {
        if (display == null) {
            return new ItemStack(Material.STONE);
        }
        String materialName = resolveText(player, display.getString("material", "STONE"), session);
        int amount = Math.max(1, Math.min(64, display.getInt("amount", 1)));
        ItemStack item = org.katacr.katpa.util.ItemStacks.resolve(materialName, player, Material.STONE);
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = display.getString("name", "");
        if (!name.isEmpty()) {
            org.katacr.katpa.text.TextParser.applyName(meta, resolveText(player, name, session));
        }
        List<String> lore = display.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> resolved = new java.util.ArrayList<>();
            for (String line : lore) {
                resolved.add(resolveText(player, line, session));
            }
            org.katacr.katpa.text.TextParser.applyLore(meta, resolved);
        }
        if (display.contains("custom_model_data")) {
            meta.setCustomModelData(display.getInt("custom_model_data"));
        }
        if (display.contains("custom_data")) {
            String raw = resolveText(player, display.getString("custom_data", ""), session).trim();
            if (!raw.isEmpty()) {
                try {
                    meta.setCustomModelData(Integer.parseInt(raw));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (display.contains("item_model")) {
            String model = resolveText(player, display.getString("item_model"), session);
            if (model != null && !model.isBlank()) {
                org.katacr.katpa.text.BukkitItemMetaCompat.setItemModel(meta, model);
            }
        }
        if (display.contains("skull_owner") && meta instanceof SkullMeta skull) {
            String owner = resolveText(player, display.getString("skull_owner"), session);
            if (owner != null && !owner.isBlank()) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    /** 替换文本中的 {key} 占位符（来自会话变量）与 PAPI（%xxx%）。 */
    private String resolveText(Player player, String text, MenuSession session) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (Map.Entry<String, String> entry : session.variables().entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
        }
        return result;
    }

    /** 执行单个动作字符串。 */
    private void executeAction(Player player, String action, MenuSession session) {
        if (action == null || action.isBlank()) {
            return;
        }
        String raw = resolveText(player, action, session);
        String resolved = ChatColor.translateAlternateColorCodes('&', raw);
        if (resolved.startsWith("open:")) {
            String target = resolved.substring(5).trim();
            Map<String, String> inherited = new HashMap<>(session.variables());
            Bukkit.getScheduler().runTask(plugin, () -> {
                String[] parts = target.split(":", 2);
                String menuId = parts[0];
                String targetArgs = parts.length > 1 ? parts[1] : "";
                openMenu(player, menuId, targetArgs, inherited);
            });
            return;
        }
        if (resolved.equals("close")) {
            player.closeInventory();
            return;
        }
        if (resolved.startsWith("tell:")) {
            org.katacr.katpa.text.AdventureSender.sendMessage(player, org.katacr.katpa.text.TextParser.parse(raw.substring(5).trim()));
            return;
        }
        if (resolved.startsWith("actionbar:")) {
            org.katacr.katpa.text.AdventureSender.sendActionBar(player, org.katacr.katpa.text.TextParser.parse(raw.substring(10).trim()));
            return;
        }
        if (resolved.startsWith("command:")) {
            player.performCommand(resolved.substring(8).trim());
            return;
        }
        if (resolved.startsWith("console:")) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved.substring(8).trim());
            return;
        }
        if (resolved.startsWith("katpa:")) {
            String payload = resolved.substring(6).trim();
            String[] handlerParts = payload.split("\\s+", 2);
            String namespace = handlerParts[0].toLowerCase();
            String rest = handlerParts.length > 1 ? handlerParts[1] : "";
            GuiActionHandler handler = actionHandlers.get(namespace);
            if (handler != null) {
                handler.execute(player, namespace, rest, session);
            } else {
                plugin.getLogger().warning("未注册的 katpa 动作命名空间: " + namespace);
            }
            return;
        }
        plugin.getLogger().warning("未知菜单动作: " + resolved);
    }

    /** 返回玩家当前菜单会话（供动作处理器读写变量）。 */
    public MenuSession session(UUID playerId) {
        return sessions.get(playerId);
    }
}
