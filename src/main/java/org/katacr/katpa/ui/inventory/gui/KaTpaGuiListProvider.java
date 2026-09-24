package org.katacr.katpa.ui.inventory.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.Home;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.PlayerWarp;
import org.katacr.katpa.model.RelationEntry;
import org.katacr.katpa.model.TeleportRequest;
import org.katacr.katpa.model.Warp;
import org.katacr.katpa.service.RequestService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KaTpa 列表型按钮数据提供器，按 {@code type} 填充请求列表、在线玩家、地标、家与名单成员。
 *
 * 每个条目提供展示物品与一组点击时可注入会话的变量（如 {@code {player_name}}、
 * {@code {request_id}}、{@code {warp_name}}），供菜单按钮的 actions 引用。
 */
public final class KaTpaGuiListProvider implements GuiListProvider {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件实例的列表提供器。 */
    public KaTpaGuiListProvider(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 读取语言文本并替换占位符（列表条目 lore 统一走 i18n，键前缀 {@code gui.list.}）。 */
    private String lang(String key, Map<String, String> vars) {
        return plugin.messages().text(key, vars == null ? Map.of() : vars);
    }

    /** 读取不含占位符的语言文本。 */
    private String lang(String key) {
        return lang(key, Map.of());
    }

    @Override
    public List<GuiListItem> provide(PlayerLike player, String type, MenuSession session, int page, int perPage) {
        if (type == null) {
            return java.util.Collections.emptyList();
        }
        switch (type) {
            case "REQUEST_LIST" -> {
                return buildRequests(player.getBukkit(), page, perPage);
            }
            case "ONLINE_PLAYERS" -> {
                return buildOnlinePlayers(player.getBukkit(), page, perPage);
            }
            case "WARP_LIST" -> {
                return buildWarps(player.getBukkit(), session, page, perPage);
            }
            case "PWARP_LIST" -> {
                return buildPlayerWarps(player.getBukkit(), session, page, perPage);
            }
            case "PWARP_LEADERBOARD" -> {
                return buildPwarpLeaderboard(player.getBukkit(), session, page, perPage);
            }
            case "PWARP_HISTORY" -> {
                return buildPwarpHistory(player.getBukkit(), session, page, perPage);
            }
            case "PWARP_FAVORITE" -> {
                return buildPwarpFavorite(player.getBukkit(), session, page, perPage);
            }
            case "PWARP_OWNERS" -> {
                return buildPwarpOwners(player.getBukkit(), session, page, perPage);
            }
            case "PWARP_OWNER_LIST" -> {
                return buildPwarpOwnerList(player.getBukkit(), session, page, perPage);
            }
            case "PWARP_STARS" -> {
                return buildPwarpStars(session, perPage);
            }
            case "HOME_LIST" -> {
                return buildHomes(player.getBukkit(), page, perPage);
            }
            case "RELATION_LIST" -> {
                return buildRelations(player.getBukkit(), session.args(), page, perPage);
            }
            default -> {
                return java.util.Collections.emptyList();
            }
        }
    }

    /** 按页码从全量列表中截取当前页条目。 */
    private <T> List<T> pageOf(List<T> all, int page, int perPage) {
        if (perPage <= 0) {
            return all;
        }
        int from = page * perPage;
        if (from >= all.size()) {
            return java.util.Collections.emptyList();
        }
        int to = Math.min(all.size(), from + perPage);
        return new ArrayList<>(all.subList(from, to));
    }

    /** 构建接收者的待处理请求列表；点击发送接受指令。 */
    private List<GuiListItem> buildRequests(Player player, int page, int perPage) {
        RequestService requests = plugin.requests();
        if (requests == null) {
            return java.util.Collections.emptyList();
        }
        List<GuiListItem> items = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<TeleportRequest> all = requests.incoming(player.getUniqueId());
        for (TeleportRequest request : pageOf(all, page, perPage)) {
            String senderName = requests.senderName(request);
            String typeDisplay = lang(request.type() == org.katacr.katpa.model.RequestType.TPA_HERE
                    ? "gui.list.request.type-tpahere" : "gui.list.request.type-tpa");
            long remaining = Math.max(0, (request.expiresAt() - now) / 1000);
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(senderName));
                skull.setDisplayName("&a" + senderName);
                skull.setLore(java.util.List.of(
                        lang("gui.list.request.lore-type", Map.of("type", typeDisplay)),
                        lang("gui.list.request.lore-remaining", Map.of("seconds", Long.toString(remaining))),
                        "",
                        lang("gui.list.request.lore-accept"),
                        lang("gui.list.request.lore-deny")));
                item.setItemMeta(skull);
            }
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("request_id", request.id().toString());
            vars.put("sender_name", senderName);
            vars.put("player_name", senderName);
            vars.put("request_type_display", typeDisplay);
            vars.put("request_remaining", String.valueOf(remaining));
            items.add(new GuiListItem(item, vars));
        }
        return items;
    }

    /** 构建在线玩家列表（不含自己）；优先用 KaProxy 全服数据，回退到本服。 */
    private List<GuiListItem> buildOnlinePlayers(Player viewer, int page, int perPage) {
        List<GuiListItem> items = new ArrayList<>();
        List<org.katacr.katpa.model.NetworkPlayer> all;
        if (plugin.network() != null && plugin.network().available()) {
            all = plugin.network().onlinePlayers().stream()
                    .filter(p -> !p.id().equals(viewer.getUniqueId()))
                    .toList();
        } else {
            all = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getUniqueId().equals(viewer.getUniqueId())) {
                    all.add(new org.katacr.katpa.model.NetworkPlayer(p.getUniqueId(), p.getName(), "local"));
                }
            }
        }
        for (org.katacr.katpa.model.NetworkPlayer p : pageOf(all, page, perPage)) {
            String serverDisplay = "local".equals(p.server()) ? lang("gui.list.online.server-local") : p.server();
            ItemStack item = skull(p.name(), "&a" + p.name(), java.util.List.of(
                    lang("gui.list.online.lore-server", Map.of("server", serverDisplay)),
                    lang("gui.list.online.lore-hint"),
                    lang("gui.list.online.lore-click")));
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("player_name", p.name());
            vars.put("target_uuid", p.id().toString());
            items.add(new GuiListItem(item, vars));
        }
        return items;
    }

    /** 构建地标列表；点击传送或编辑（取决于菜单）。 */
    private List<GuiListItem> buildWarps(Player player, MenuSession session, int page, int perPage) {
        if (plugin.warpStore() == null) {
            return java.util.Collections.emptyList();
        }
        List<GuiListItem> items = new ArrayList<>();
        List<Warp> all = plugin.warpStore().all();
        for (Warp warp : pageOf(all, page, perPage)) {
            ItemStack item = buildIconItem(warp.iconMaterial(), warp.iconCustomData(), warp.iconItemModel(),
                    Warp.DEFAULT_ICON);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("&a" + warp.name());
                java.util.List<String> lore = new java.util.ArrayList<>(java.util.List.of(
                        lang("gui.list.warp.lore-world", Map.of("world", warp.displayWorld())),
                        lang("gui.list.warp.lore-server", Map.of("server", warp.displayServer())),
                        lang("gui.list.warp.lore-cooldown", Map.of("seconds", String.valueOf(warp.cooldownSeconds()))),
                        lang("gui.list.warp.lore-cost", Map.of("cost", String.valueOf(warp.cost())))));
                if (warp.description() != null && !warp.description().isBlank()) {
                    lore.add(lang("gui.list.warp.lore-description", Map.of("description", warp.description())));
                }
                lore.add("");
                boolean manager = session != null && "warp_manager".equals(session.menuId());
                if (manager && player.hasPermission("katpa.warp.admin")) {
                    lore.add(lang("gui.list.warp.lore-edit-left"));
                } else {
                    lore.add(lang("gui.list.warp.lore-teleport-left"));
                }
                if (player.hasPermission("katpa.warp.admin")) {
                    lore.add(lang("gui.list.warp.lore-edit-right"));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("warp_name", warp.name());
            vars.put("warp_permission", warp.permission() == null ? "" : warp.permission());
            vars.put("warp_cooldown", String.valueOf(warp.cooldownSeconds()));
            vars.put("warp_cost", String.valueOf(warp.cost()));
            vars.put("warp_world", warp.displayWorld());
            vars.put("warp_server", warp.displayServer());
            vars.put("warp_description", warp.description() == null ? "" : warp.description());
            vars.put("warp_icon", warp.iconMaterial() == null ? Warp.DEFAULT_ICON : warp.iconMaterial());
            vars.put("warp_custom_data", warp.iconCustomData() == null ? "" : String.valueOf(warp.iconCustomData()));
            vars.put("warp_item_model", warp.iconItemModel() == null ? "" : warp.iconItemModel());
            items.add(new GuiListItem(item, vars));
        }
        return items;
    }

    /** 构建玩家地标列表（全局或自己的）；自己的视图按“已有→已解锁空槽→未解锁”展示。 */
    private List<GuiListItem> buildPlayerWarps(Player player, MenuSession session, int page, int perPage) {
        if (plugin.playerWarpStore() == null) {
            return java.util.Collections.emptyList();
        }
        List<GuiListItem> items = new ArrayList<>();
        boolean ownMenu = session != null && "pwarp_manager".equals(session.menuId());
        List<PlayerWarp> all = ownMenu ? plugin.playerWarpStore().byOwner(player.getUniqueId())
                : plugin.playerWarpStore().all();
        for (PlayerWarp warp : all) {
            items.add(buildPwarpListItem(player, warp, ownMenu));
        }
        if (ownMenu) {
            int totalSlots = Math.max(0, plugin.getConfig().getInt("modules.pwarp.total-slots", 10));
            int maxWarps = plugin.playerWarp() != null ? plugin.playerWarp().maxWarps(player) : totalSlots;
            int total = Math.max(totalSlots, all.size());
            int empty = Math.max(0, Math.min(maxWarps - all.size(), total - all.size()));
            for (int i = 0; i < empty; i++) {
                items.add(new GuiListItem(null, Map.of(), GuiListItem.Kind.EMPTY));
            }
            int locked = total - items.size();
            for (int i = 0; i < locked; i++) {
                items.add(new GuiListItem(null, Map.of(), GuiListItem.Kind.LOCK));
            }
        }
        return pageOf(items, page, perPage);
    }

    /** 构建单条玩家地标列表项（列表展示用，含创建者/评分等）。 */
    private GuiListItem buildPwarpListItem(Player player, PlayerWarp warp, boolean ownMenu) {
        ItemStack item = buildIconItem(warp.iconMaterial(), warp.iconCustomData(), warp.iconItemModel(),
                PlayerWarp.DEFAULT_ICON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("&a" + warp.name());
            double avg = plugin.warpRatingStore() != null
                    ? plugin.warpRatingStore().averageStars(warp.id()) : 0;
            int score = plugin.warpRatingStore() != null
                    ? plugin.warpRatingStore().totalScore(warp.id()) : 0;
            java.util.List<String> lore = new java.util.ArrayList<>(java.util.List.of(
                    lang("gui.list.pwarp.lore-owner", Map.of("owner", String.valueOf(warp.ownerName()))),
                    lang("gui.list.pwarp.lore-description",
                            Map.of("description", warp.description() == null ? "" : warp.description())),
                    lang("gui.list.pwarp.lore-cost", Map.of("cost", String.valueOf(warp.cost()))),
                    lang("gui.list.pwarp.lore-stars-score",
                            Map.of("stars", String.format("%.1f", avg), "score", String.valueOf(score)))));
            lore.add("");
            if (ownMenu) {
                lore.add(lang("gui.list.pwarp.lore-edit-left"));
                lore.add(lang("gui.list.pwarp.lore-edit-right"));
            } else {
                lore.add(lang("gui.list.pwarp.lore-teleport-left"));
                boolean fav = plugin.pwarpMeta() != null
                        && plugin.pwarpMeta().isFavorite(player.getUniqueId(), warp.id());
                lore.add(fav ? lang("gui.list.pwarp.lore-unfavorite-q") : lang("gui.list.pwarp.lore-favorite-q"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("pwarp_name", warp.name());
        vars.put("pwarp_owner", warp.ownerName() == null ? "" : warp.ownerName());
        vars.put("pwarp_description", warp.description() == null ? "" : warp.description());
        vars.put("pwarp_cost", String.valueOf(warp.cost()));
        vars.put("pwarp_stars", String.format("%.1f", plugin.warpRatingStore() != null
                ? plugin.warpRatingStore().averageStars(warp.id()) : 0));
        vars.put("pwarp_score", String.valueOf(plugin.warpRatingStore() != null
                ? plugin.warpRatingStore().totalScore(warp.id()) : 0));
        vars.put("pwarp_icon", warp.iconMaterial() == null ? PlayerWarp.DEFAULT_ICON : warp.iconMaterial());
        vars.put("pwarp_custom_data", warp.iconCustomData() == null ? "" : String.valueOf(warp.iconCustomData()));
        vars.put("pwarp_item_model", warp.iconItemModel() == null ? "" : warp.iconItemModel());
        vars.put("pwarp_favorite", String.valueOf(plugin.pwarpMeta() != null
                && plugin.pwarpMeta().isFavorite(player.getUniqueId(), warp.id())));
        return new GuiListItem(item, vars, GuiListItem.Kind.NORMAL);
    }

    /** 构建玩家地标排行榜（按累计加权得分降序），数据取自排行榜缓存，不再逐条查库。 */
    private List<GuiListItem> buildPwarpLeaderboard(Player player, MenuSession session, int page, int perPage) {
        if (plugin.playerWarpStore() == null || plugin.warpRatingStore() == null) {
            return java.util.Collections.emptyList();
        }
        List<GuiListItem> items = new ArrayList<>();
        List<org.katacr.katpa.storage.WarpRatingStore.LeaderboardEntry> ranked =
                plugin.warpRatingStore().leaderboard(0);
        int rankBase = page * perPage;
        int end = perPage == Integer.MAX_VALUE ? ranked.size() : Math.min(ranked.size(), rankBase + perPage);
        for (int i = rankBase; i < end; i++) {
            org.katacr.katpa.storage.WarpRatingStore.LeaderboardEntry entry = ranked.get(i);
            PlayerWarp warp = plugin.playerWarpStore().find(entry.warpId());
            if (warp == null) {
                continue;
            }
            ItemStack item = buildIconItem(warp.iconMaterial(), warp.iconCustomData(), warp.iconItemModel(),
                    PlayerWarp.DEFAULT_ICON);
            ItemMeta meta = item.getItemMeta();
            double avg = entry.stars();
            int score = entry.score();
            if (meta != null) {
                meta.setDisplayName("&a#" + (i + 1) + " " + warp.name());
                java.util.List<String> lore = new java.util.ArrayList<>(java.util.List.of(
                        lang("gui.list.pwarp.lore-owner", Map.of("owner", String.valueOf(warp.ownerName()))),
                        lang("gui.list.pwarp.lore-description",
                                Map.of("description", warp.description() == null ? "" : warp.description())),
                        lang("gui.list.pwarp.lore-stars-score",
                                Map.of("stars", String.format("%.1f", avg), "score", String.valueOf(score))),
                        lang("gui.list.pwarp.lore-teleport-cost", Map.of("cost", String.valueOf(warp.cost())))));
                lore.add("");
                lore.add(lang("gui.list.pwarp.lore-teleport-left"));
                if (!warp.ownerId().equals(player.getUniqueId())) {
                    lore.add(lang("gui.list.pwarp.lore-rate-right"));
                }
                boolean leaderboardFav = plugin.pwarpMeta() != null
                        && plugin.pwarpMeta().isFavorite(player.getUniqueId(), warp.id());
                lore.add(leaderboardFav ? lang("gui.list.pwarp.lore-unfavorite-q")
                        : lang("gui.list.pwarp.lore-favorite-q"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("pwarp_rank", String.valueOf(i + 1));
            vars.put("pwarp_name", warp.name());
            vars.put("pwarp_owner", warp.ownerName() == null ? "" : warp.ownerName());
            vars.put("pwarp_description", warp.description() == null ? "" : warp.description());
            vars.put("pwarp_cost", String.valueOf(warp.cost()));
            vars.put("pwarp_stars", String.format("%.1f", avg));
            vars.put("pwarp_score", String.valueOf(score));
            vars.put("pwarp_icon", warp.iconMaterial() == null ? PlayerWarp.DEFAULT_ICON : warp.iconMaterial());
            vars.put("pwarp_custom_data", warp.iconCustomData() == null ? "" : String.valueOf(warp.iconCustomData()));
            vars.put("pwarp_item_model", warp.iconItemModel() == null ? "" : warp.iconItemModel());
            items.add(new GuiListItem(item, vars));
        }
        return items;
    }

    /** 构建玩家历史传送过的玩家地标列表（按最近访问倒序）。 */
    private List<GuiListItem> buildPwarpHistory(Player player, MenuSession session, int page, int perPage) {
        if (plugin.playerWarpStore() == null || plugin.pwarpMeta() == null) {
            return java.util.Collections.emptyList();
        }
        List<GuiListItem> items = new ArrayList<>();
        List<UUID> ids = plugin.pwarpMeta().history(player.getUniqueId());
        for (UUID id : pageOf(ids, page, perPage)) {
            PlayerWarp warp = plugin.playerWarpStore().find(id);
            if (warp == null) {
                continue;
            }
            items.add(buildPwarpItem(player, warp, false, true, false));
        }
        return items;
    }

    /** 构建玩家收藏的玩家地标列表。 */
    private List<GuiListItem> buildPwarpFavorite(Player player, MenuSession session, int page, int perPage) {
        if (plugin.playerWarpStore() == null || plugin.pwarpMeta() == null) {
            return java.util.Collections.emptyList();
        }
        List<GuiListItem> items = new ArrayList<>();
        List<UUID> ids = plugin.pwarpMeta().favorites(player.getUniqueId());
        for (UUID id : pageOf(ids, page, perPage)) {
            PlayerWarp warp = plugin.playerWarpStore().find(id);
            if (warp == null) {
                continue;
            }
            items.add(buildPwarpItem(player, warp, false, true, true));
        }
        return items;
    }

    /** 构建拥有至少一个玩家地标的玩家头颅列表（点击进入该玩家地标筛选）。 */
    private List<GuiListItem> buildPwarpOwners(Player player, MenuSession session, int page, int perPage) {
        if (plugin.playerWarpStore() == null || plugin.pwarpMeta() == null) {
            return java.util.Collections.emptyList();
        }
        List<GuiListItem> items = new ArrayList<>();
        Set<UUID> owners = plugin.pwarpMeta().ownerIds();
        List<String> sorted = new ArrayList<>();
        Map<String, UUID> nameToId = new HashMap<>();
        for (UUID id : owners) {
            String name = plugin.settings().knownName(id);
            if (name == null || name.isBlank()) {
                name = id.toString();
            }
            sorted.add(name);
            nameToId.put(name.toLowerCase(Locale.ROOT), id);
        }
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        for (String name : pageOf(sorted, page, perPage)) {
            UUID ownerId = nameToId.get(name.toLowerCase(Locale.ROOT));
            ItemStack item = skull(name, "&a" + name, java.util.List.of(
                    lang("gui.list.pwarp-owner.lore-hint"), lang("gui.list.pwarp-owner.lore-click")));
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("owner_name", name);
            vars.put("owner_uuid", ownerId != null ? ownerId.toString() : "");
            items.add(new GuiListItem(item, vars));
        }
        return items;
    }

    /**
     * 构建评分星级选择列表：1-5 星，选中值（会话变量 {@code pwarp_rate_selected}，默认 5）
     * 及其左侧为点亮状态（{@link GuiListItem.Kind#LIT}），右侧为未点亮（{@link GuiListItem.Kind#UNLIT}）。
     */
    private List<GuiListItem> buildPwarpStars(MenuSession session, int perPage) {
        int total = Math.max(1, Math.min(5, perPage));
        int selected = 5;
        if (session != null) {
            try {
                selected = Integer.parseInt(session.get("pwarp_rate_selected"));
            } catch (NumberFormatException ignored) {
            }
        }
        selected = Math.max(1, Math.min(total, selected));
        List<GuiListItem> items = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            Map<String, String> vars = new HashMap<>();
            vars.put("pwarp_star", String.valueOf(i));
            items.add(new GuiListItem(null, vars,
                    i <= selected ? GuiListItem.Kind.LIT : GuiListItem.Kind.UNLIT));
        }
        return items;
    }

    /** 构建指定创建者拥有的玩家地标列表（来自 PWARP_OWNERS 的点击参数）。 */
    private List<GuiListItem> buildPwarpOwnerList(Player player, MenuSession session, int page, int perPage) {
        if (plugin.playerWarpStore() == null || plugin.pwarpMeta() == null) {
            return java.util.Collections.emptyList();
        }
        String ownerArg = session != null ? session.args() : "";
        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerArg);
        } catch (IllegalArgumentException e) {
            return java.util.Collections.emptyList();
        }
        List<GuiListItem> items = new ArrayList<>();
        List<UUID> ids = plugin.pwarpMeta().byOwner(ownerId);
        for (UUID id : pageOf(ids, page, perPage)) {
            PlayerWarp warp = plugin.playerWarpStore().find(id);
            if (warp == null) {
                continue;
            }
            items.add(buildPwarpItem(player, warp, false, true, false));
        }
        return items;
    }

    /** 构建玩家地标列表项物品与变量；favorite 视图显示收藏/取消提示。 */
    private GuiListItem buildPwarpItem(Player player, PlayerWarp warp, boolean ownMenu,
                                       boolean showFavorite, boolean isFavoriteView) {
        ItemStack item = buildIconItem(warp.iconMaterial(), warp.iconCustomData(), warp.iconItemModel(),
                PlayerWarp.DEFAULT_ICON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("&a" + warp.name());
            boolean fav = plugin.pwarpMeta().isFavorite(player.getUniqueId(), warp.id());
            double stars = plugin.warpRatingStore() != null
                    ? plugin.warpRatingStore().averageStars(warp.id()) : 0;
            java.util.List<String> lore = new java.util.ArrayList<>(java.util.List.of(
                    lang("gui.list.pwarp.lore-owner", Map.of("owner", String.valueOf(warp.ownerName()))),
                    lang("gui.list.pwarp.lore-description",
                            Map.of("description", warp.description() == null ? "" : warp.description())),
                    lang("gui.list.pwarp.lore-cost", Map.of("cost", String.valueOf(warp.cost()))),
                    lang("gui.list.pwarp.lore-stars", Map.of("stars", String.format("%.1f", stars)))));
            lore.add("");
            if (showFavorite) {
                lore.add(lang("gui.list.pwarp.lore-teleport-left"));
                if (isFavoriteView) {
                    lore.add(lang("gui.list.pwarp.lore-unfavorite-q"));
                } else {
                    lore.add(fav ? lang("gui.list.pwarp.lore-unfavorite-q") : lang("gui.list.pwarp.lore-favorite-q"));
                }
            } else {
                lore.add(lang("gui.list.pwarp.lore-teleport-left"));
                if (!warp.ownerId().equals(player.getUniqueId())) {
                    lore.add(lang("gui.list.pwarp.lore-rate-right"));
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("pwarp_name", warp.name());
        vars.put("pwarp_owner", warp.ownerName() == null ? "" : warp.ownerName());
        vars.put("pwarp_description", warp.description() == null ? "" : warp.description());
        vars.put("pwarp_cost", String.valueOf(warp.cost()));
        vars.put("pwarp_stars", String.format("%.1f", plugin.warpRatingStore() != null
                ? plugin.warpRatingStore().averageStars(warp.id()) : 0));
        vars.put("pwarp_score", String.valueOf(plugin.warpRatingStore() != null
                ? plugin.warpRatingStore().totalScore(warp.id()) : 0));
        vars.put("pwarp_favorite", String.valueOf(plugin.pwarpMeta().isFavorite(player.getUniqueId(), warp.id())));
        vars.put("pwarp_icon", warp.iconMaterial() == null ? PlayerWarp.DEFAULT_ICON : warp.iconMaterial());
        vars.put("pwarp_custom_data", warp.iconCustomData() == null ? "" : String.valueOf(warp.iconCustomData()));
        vars.put("pwarp_item_model", warp.iconItemModel() == null ? "" : warp.iconItemModel());
        return new GuiListItem(item, vars);
    }

    /** 构建玩家个人家列表：已有家 → 已解锁空槽 → 未解锁锁定槽，按页码截取。 */
    private List<GuiListItem> buildHomes(Player player, int page, int perPage) {
        if (plugin.homeStore() == null) {
            return java.util.Collections.emptyList();
        }
        List<Home> all = plugin.homeStore().all(player.getUniqueId());
        int totalSlots = Math.max(0, plugin.getConfig().getInt("modules.home.total-slots", 10));
        int maxHomes = plugin.home() != null ? plugin.home().maxHomes(player) : totalSlots;

        List<GuiListItem> sequence = new ArrayList<>();
        for (Home home : all) {
            ItemStack item = buildIconItem(home.iconMaterial(), home.iconCustomData(), home.iconItemModel(),
                    Home.DEFAULT_ICON);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("&a" + home.name());
                java.util.List<String> lore = new java.util.ArrayList<>(java.util.List.of(
                        lang("gui.list.home.lore-world", Map.of("world", home.displayWorld()))));
                if (home.description() != null && !home.description().isBlank()) {
                    lore.add("&7" + home.description());
                }
                lore.add(lang("gui.list.home.lore-teleport"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("home_name", home.name());
            vars.put("home_world", home.displayWorld());
            vars.put("home_server", home.displayServer());
            vars.put("home_description", home.description() == null ? "" : home.description());
            sequence.add(new GuiListItem(item, vars, GuiListItem.Kind.NORMAL));
        }
        int total = Math.max(totalSlots, all.size());
        int empty = Math.max(0, Math.min(maxHomes - all.size(), total - all.size()));
        for (int i = 0; i < empty; i++) {
            sequence.add(new GuiListItem(null, Map.of(), GuiListItem.Kind.EMPTY));
        }
        int locked = total - sequence.size();
        for (int i = 0; i < locked; i++) {
            sequence.add(new GuiListItem(null, Map.of(), GuiListItem.Kind.LOCK));
        }
        return pageOf(sequence, page, perPage);
    }

    /** 根据图标字段构建物品，字段为空时回退默认材质。 */
    private ItemStack buildIconItem(String material, Integer customData, String itemModel, String defaultMaterial) {
        ItemStack item = org.katacr.katpa.util.ItemStacks.resolve(
                material != null && !material.isBlank() ? material : defaultMaterial, Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (customData != null) {
                meta.setCustomModelData(customData);
            }
            if (itemModel != null && !itemModel.isBlank()) {
                org.katacr.katpa.text.BukkitItemMetaCompat.setItemModel(meta, itemModel);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 构建名单成员列表；点击移除。 */
    private List<GuiListItem> buildRelations(Player player, String args, int page, int perPage) {
        ListType type = "blacklist".equalsIgnoreCase(args) ? ListType.BLACKLIST : ListType.WHITELIST;
        List<GuiListItem> items = new ArrayList<>();
        List<RelationEntry> all = plugin.settings().relations(player.getUniqueId(), type);
        for (RelationEntry entry : pageOf(all, page, perPage)) {
            ItemStack item = skull(entry.targetName(), "&a" + entry.targetName(), java.util.List.of(
                    lang("gui.list.relation.lore-hint"), lang("gui.list.relation.lore-click")));
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("member_name", entry.targetName());
            vars.put("member_uuid", entry.targetId().toString());
            items.add(new GuiListItem(item, vars));
        }
        return items;
    }

    /** 构建带玩家头颅的物品。 */
    private ItemStack skull(String ownerName, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
            skull.setDisplayName(displayName);
            skull.setLore(lore);
            item.setItemMeta(skull);
        }
        return item;
    }

    /** 反射调用 ItemMeta.setItemModel（1.21.4+ 才有），旧版本自动跳过。 */
    public static void setItemModelReflect(ItemMeta meta, String model) {
        try {
            Class<?> itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta");
            Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            var method = itemMetaClass.getMethod("setItemModel", namespacedKeyClass);
            var constructor = namespacedKeyClass.getDeclaredConstructor(String.class, String.class);
            String[] parts = model.split(":", 2);
            if (parts.length != 2) {
                return;
            }
            Object modelKey = constructor.newInstance(parts[0], parts[1]);
            method.invoke(meta, modelKey);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            // 1.21.4 以下版本不支持 item_model，忽略
        }
    }

    /** 反射调用 ItemMeta.getItemModel（1.21.4+ 才有），旧版本返回 null。 */
    public static String readItemModelReflect(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        try {
            Class<?> itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta");
            var method = itemMetaClass.getMethod("getItemModel");
            Object result = method.invoke(meta);
            return result == null ? null : result.toString();
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return null;
        }
    }
}
