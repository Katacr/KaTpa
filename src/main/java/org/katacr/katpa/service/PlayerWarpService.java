package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.PlayerWarp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 管理玩家创建的公共地标：创建、传送、编辑、评分与传送收入结算。 */
public final class PlayerWarpService {
    private final KaTpaPlugin plugin;
    /** 玩家地标全局冷却：玩家 UUID → 冷却结束时间戳（毫秒）。 */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    /** 创建绑定插件服务的玩家地标服务。 */
    public PlayerWarpService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 执行 /pwarp <名称>，检查冷却和费用后传送；创建者本人免费。 */
    public void warp(Player player, String name) {
        warp(player, name, false);
    }

    /**
     * 执行玩家地标传送。
     *
     * <p>{@code confirmed} 为 false 且目标收费时，仅发送可点击的二次确认消息
     * （{@code [确定]} 继续传送、{@code [取消]} 放弃），不直接扣费。
     */
    public void warp(Player player, String name, boolean confirmed) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "pwarp-not-found", Map.of("name", name));
            return;
        }
        long remaining = cooldownRemaining(player);
        if (remaining > 0L) {
            plugin.messages().send(player, "pwarp-cooldown", Map.of("seconds", Long.toString(remaining)));
            return;
        }
        boolean isOwner = warp.ownerId().equals(player.getUniqueId());
        double cost = isOwner ? 0 : warp.cost();
        if (cost > 0 && !confirmed) {
            sendCostConfirm(player, warp, cost);
            return;
        }
        if (cost > 0 && !payCost(player, cost)) {
            plugin.messages().send(player, "pwarp-insufficient-funds", Map.of("cost", String.format("%.2f", cost)));
            return;
        }
        String currentServer = plugin.network().serverId();
        if (warp.server().equals(currentServer) || !plugin.network().enabled()) {
            teleportLocal(player, warp, isOwner, cost);
            return;
        }
        if (!plugin.network().available()) {
            plugin.messages().send(player, "proxy-unavailable");
            return;
        }
        teleportCrossServer(player, warp, isOwner, cost);
    }

    /** 发送收费地标的可点击二次确认消息：第 1 个区域 [确定] 继续传送，第 2 个区域 [取消] 放弃。 */
    private void sendCostConfirm(Player player, PlayerWarp warp, double cost) {
        String text = plugin.messages().text("pwarp-cost-confirm", Map.of(
                "name", warp.name(),
                "cost", String.format("%.2f", cost)));
        org.katacr.katpa.text.ClickableText.sendClickableMulti(player, text, (index, label) -> {
            if (index == 0) {
                return p -> warp(p, warp.name(), true);
            }
            if (index == 1) {
                return p -> plugin.messages().send(p, "pwarp-cost-cancelled", Map.of("name", warp.name()));
            }
            return null;
        }, java.time.Duration.ofSeconds(30));
    }

    /** 玩家创建地标，检查数量上限后写入。 */
    public boolean create(Player player, String name) {
        if (plugin.teleports().isCurrentWorldDisabled("pwarp", player)) {
            plugin.messages().send(player, "world-creation-disabled");
            return false;
        }
        if (name == null || name.isBlank()) {
            plugin.messages().send(player, "pwarp-name-empty");
            return false;
        }
        int maxLen = plugin.getConfig().getInt("modules.pwarp.name-max-length", 32);
        if (name.length() > maxLen) {
            plugin.messages().send(player, "pwarp-name-too-long", Map.of("max", Integer.toString(maxLen)));
            return false;
        }
        if (!player.hasPermission("katpa.pwarp.create")) {
            plugin.messages().send(player, "pwarp-no-create-permission");
            return false;
        }
        if (plugin.playerWarpStore().find(name) != null) {
            plugin.messages().send(player, "pwarp-name-exists", Map.of("name", name));
            return false;
        }
        if (plugin.playerWarpStore().count(player.getUniqueId()) >= maxWarps(player)) {
            plugin.messages().send(player, "pwarp-limit", Map.of("max", Integer.toString(maxWarps(player))));
            return false;
        }
        if (!chargeCreateCost(player)) {
            return false;
        }
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        String serverId = plugin.network().displayServerId();
        String world = loc.getWorld().getName();
        String worldAlias = org.katacr.katpa.util.WorldNames.display(world);
        long now = System.currentTimeMillis();
        PlayerWarp warp = new PlayerWarp(
                UUID.randomUUID(),
                player.getUniqueId(),
                player.getName(),
                name, server, serverId, world, worldAlias,
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                "",
                "",
                null,
                "",
                plugin.getConfig().getDouble("modules.pwarp.default-cost", 0),
                now);
        plugin.playerWarpStore().create(warp);
        plugin.messages().send(player, "pwarp-created", Map.of("name", name));
        return true;
    }

    /** 删除玩家自己的地标（或管理员删除任意地标）。 */
    public boolean delete(Player player, String name) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "pwarp-not-found", Map.of("name", name));
            return false;
        }
        if (!canEdit(player, warp)) {
            plugin.messages().send(player, "pwarp-no-edit-permission");
            return false;
        }
        plugin.playerWarpStore().remove(warp.ownerId(), name);
        plugin.messages().send(player, "pwarp-deleted", Map.of("name", name));
        return true;
    }

    /** 重命名玩家地标，返回是否成功。 */
    public boolean rename(Player player, String oldName, String newName) {
        PlayerWarp warp = plugin.playerWarpStore().find(oldName);
        if (warp == null) {
            plugin.messages().send(player, "pwarp-not-found", Map.of("name", oldName));
            return false;
        }
        if (!canEdit(player, warp)) {
            plugin.messages().send(player, "pwarp-no-edit-permission");
            return false;
        }
        int maxLen = plugin.getConfig().getInt("modules.pwarp.name-max-length", 32);
        if (newName.length() > maxLen) {
            plugin.messages().send(player, "pwarp-name-too-long", Map.of("max", Integer.toString(maxLen)));
            return false;
        }
        if (plugin.playerWarpStore().find(newName) != null) {
            plugin.messages().send(player, "pwarp-name-exists", Map.of("name", newName));
            return false;
        }
        plugin.playerWarpStore().remove(warp.ownerId(), oldName);
        plugin.playerWarpStore().save(build(warp, b -> b.name(newName)));
        plugin.messages().send(player, "pwarp-renamed", Map.of("old", oldName, "new", newName));
        return true;
    }

    /** 更新地标描述。 */
    public void setDescription(Player player, String name, String description) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) return;
        if (!canEdit(player, warp)) {
            plugin.messages().send(player, "pwarp-no-edit-permission");
            return;
        }
        int maxLen = plugin.getConfig().getInt("modules.pwarp.description-max-length", 100);
        if (description.length() > maxLen) {
            plugin.messages().send(player, "pwarp-desc-too-long", Map.of("max", Integer.toString(maxLen)));
            return;
        }
        plugin.playerWarpStore().save(build(warp, b -> b.description(description)));
    }

    /** 更新地标传送费用。 */
    public void setCost(Player player, String name, double cost) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) return;
        if (!canEdit(player, warp)) {
            plugin.messages().send(player, "pwarp-no-edit-permission");
            return;
        }
        plugin.playerWarpStore().save(build(warp, b -> b.cost(Math.max(0, cost))));
    }

    /** 以玩家当前位置更新地标坐标/世界/服务器，保留描述、图标与费用。 */
    public boolean updateLocation(Player player, String name) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "pwarp-not-found", Map.of("name", name));
            return false;
        }
        if (!canEdit(player, warp)) {
            plugin.messages().send(player, "pwarp-no-edit-permission");
            return false;
        }
        if (plugin.teleports().isCurrentWorldDisabled("pwarp", player)) {
            plugin.messages().send(player, "world-creation-disabled");
            return false;
        }
        Location loc = player.getLocation();
        String world = loc.getWorld().getName();
        plugin.playerWarpStore().save(build(warp, b -> b
                .server(plugin.network().serverId())
                .serverId(plugin.network().displayServerId())
                .world(world)
                .worldAlias(org.katacr.katpa.util.WorldNames.display(world))
                .position(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch())));
        plugin.messages().send(player, "pwarp-location-updated", Map.of("name", name));
        return true;
    }

    /** 以玩家手中物品设置地标图标（material/custom_data/item_model）。 */
    public void setIcon(Player player, String name, ItemStack item) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) return;
        if (!canEdit(player, warp)) {
            plugin.messages().send(player, "pwarp-no-edit-permission");
            return;
        }
        String material = item.getType().name();
        Integer customData = item.getItemMeta() != null && item.getItemMeta().hasCustomModelData()
                ? item.getItemMeta().getCustomModelData() : null;
        String itemModel = org.katacr.katpa.ui.inventory.gui.KaTpaGuiListProvider.readItemModelReflect(item.getItemMeta());
        plugin.playerWarpStore().save(build(warp, b -> b.iconMaterial(material)
                .iconCustomData(customData).iconItemModel(itemModel == null ? "" : itemModel)));
    }

    /** 记录某玩家对某地标的评分（1-5 星）；创建者不能给自己评分，重复评分覆盖上次。 */
    public void rate(Player player, String name, int stars) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "pwarp-not-found", Map.of("name", name));
            return;
        }
        if (warp.ownerId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "pwarp-rate-own");
            return;
        }
        if (stars < 1 || stars > 5) {
            plugin.messages().send(player, "pwarp-rate-invalid");
            return;
        }
        plugin.warpRatingStore().rate(player.getUniqueId(), warp.id(), stars);
        plugin.messages().send(player, "pwarp-rated", Map.of("name", name, "stars", Integer.toString(stars)));
    }

    /** 切换玩家对某玩家地标的收藏状态，并提示结果。 */
    public void toggleFavorite(Player player, String name) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "pwarp-not-found", Map.of("name", name));
            return;
        }
        boolean favorited = plugin.pwarpMeta().toggleFavorite(player.getUniqueId(), warp.id());
        if (favorited) {
            plugin.messages().send(player, "pwarp-favorited", Map.of("name", name));
        } else {
            plugin.messages().send(player, "pwarp-unfavorited", Map.of("name", name));
        }
    }

    /** 判断玩家是否已收藏某地标。 */
    public boolean isFavorite(Player player, UUID warpId) {
        return plugin.pwarpMeta().isFavorite(player.getUniqueId(), warpId);
    }

    /**
     * 重载玩家地标数据并重算排行榜缓存（供 {@code /pwarp admin reload}）。
     *
     * <p>异步重新读取 player_warp 全表到内存，随后重算前 N 名排行榜缓存。
     */
    public void reloadData() {
        if (plugin.playerWarpStore() != null) {
            plugin.playerWarpStore().reload();
        }
        if (plugin.warpRatingStore() != null) {
            plugin.warpRatingStore().refreshLeaderboard();
        }
    }

    /** 领取玩家离线期间累计的传送收入；在线直接 deposit，离线已挂账由 join 事件领取。 */
    public void claimPendingIncome(Player player) {
        double amount = plugin.warpRatingStore().takePendingIncome(player.getUniqueId());
        if (amount > 0 && plugin.economy() != null) {
            plugin.economy().depositPlayer(player, amount);
            plugin.messages().send(player, "pwarp-income-claimed", Map.of("amount", String.format("%.2f", amount)));
        }
    }

    /** 玩家可创建的玩家地标数量上限，默认 1，取权限持有最大值。 */
    public int maxWarps(Player player) {        int max = plugin.getConfig().getInt("modules.pwarp.default-amount", 1);
        for (var perm : player.getEffectivePermissions()) {
            String prefix = "katpa.pwarp.amount.";
            if (perm.getValue() && perm.getPermission().startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.getPermission().substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    /** 判断玩家是否能编辑该地标（拥有者或管理员）。 */
    private boolean canEdit(Player player, PlayerWarp warp) {
        return warp.ownerId().equals(player.getUniqueId()) || player.hasPermission("katpa.pwarp.admin");
    }

    /** 本地传送并在成功后结算传送收入给创建者。 */
    private void teleportLocal(Player player, PlayerWarp warp, boolean isOwner, double cost) {
        if (!plugin.teleports().ensureTargetAvailable("pwarp", player, warp.server(), warp.displayServer(),
                warp.world(), "pwarp-world-unloaded", Map.of("name", warp.name()))) {
            return;
        }
        Location target = new Location(
                Bukkit.getWorld(warp.world()),
                warp.x(), warp.y(), warp.z(),
                warp.yaw(), warp.pitch());
        plugin.back().recordLocation(player);
        startCooldown(player);
        plugin.teleports().beginDirect(player, "pwarp", () -> {
            plugin.back().markOwnTeleport(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.teleport(target)) {
                    plugin.messages().send(player, "teleport-failed");
                    return;
                }
                plugin.sounds().playAt(target, "teleport", "pwarp");
                plugin.messages().sendActionBar(player,
                        plugin.messages().component("pwarp-success", Map.of("name", warp.name()), false));
                org.bukkit.Bukkit.getPluginManager().callEvent(
                        new org.katacr.katpa.api.event.KaTpaEvent(player, org.katacr.katpa.api.event.KaTpaEvent.Action.WARP, warp.name()));
                plugin.pwarpMeta().recordVisit(player.getUniqueId(), warp.id());
                if (!isOwner && cost > 0) {
                    settleIncome(warp, cost);
                }
            });
        });
    }

    /** 跨服传送：先完成源服吟唱，再请求代理切服并在落点后结算传送收入给创建者。 */
    private void teleportCrossServer(Player player, PlayerWarp warp, boolean isOwner, double cost) {
        if (!plugin.teleports().ensureTargetAvailable("pwarp", player, warp.server(), warp.displayServer(),
                warp.world(), null, null)) {
            return;
        }
        plugin.back().recordLocation(player);
        startCooldown(player);
        org.katacr.katpa.model.LocationRecord loc = new org.katacr.katpa.model.LocationRecord(
                warp.server(), warp.world(), warp.x(), warp.y(), warp.z(),
                warp.yaw(), warp.pitch(), System.currentTimeMillis());
        plugin.teleports().beginDirect(player, "pwarp", () -> {
            if (!plugin.network().backRequest(player, warp.server(), loc, "player_warp", success -> {
                if (Boolean.TRUE.equals(success)) {
                    plugin.pwarpMeta().recordVisit(player.getUniqueId(), warp.id());
                    if (!isOwner && cost > 0) {
                        settleIncome(warp, cost);
                    }
                } else if (!Boolean.TRUE.equals(success)) {
                    plugin.messages().send(player, "pwarp-failed",
                            Map.of("reason", plugin.messages().text("network-reason.connect-failed")));
                }
            })) {
                plugin.messages().send(player, "proxy-unavailable");
            }
        });
    }

    /** 结算传送收入：创建者在线直接入账，离线则挂账待领取。 */
    private void settleIncome(PlayerWarp warp, double cost) {
        Player owner = Bukkit.getPlayer(warp.ownerId());
        if (owner != null && plugin.economy() != null) {
            plugin.economy().depositPlayer(owner, cost);
            plugin.messages().send(owner, "pwarp-income-received",
                    Map.of("amount", String.format("%.2f", cost), "name", warp.name()));
        } else {
            // 创建者离线（可能在其他子服或完全不在线），挂账到数据库，上线时领取
            plugin.warpRatingStore().addPendingIncome(warp.ownerId(), cost);
        }
    }

    /** 基于现有 PlayerWarp 复制字段并应用修改。 */
    private PlayerWarp build(PlayerWarp warp, java.util.function.UnaryOperator<PlayerWarpBuilder> fn) {
        PlayerWarpBuilder b = new PlayerWarpBuilder(warp);
        return fn.apply(b).build();
    }

    /** 返回玩家当前的地标传送冷却剩余秒数，0 表示可用。 */
    private long cooldownRemaining(Player player) {
        Long until = cooldowns.get(player.getUniqueId());
        if (until == null) return 0L;
        long remaining = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, remaining);
    }

    /** 记录玩家地标传送的冷却起点（全局冷却秒数，0 或负数表示不冷却）。 */
    private void startCooldown(Player player) {
        int seconds = plugin.getConfig().getInt("modules.pwarp.cooldown-seconds", 30);
        if (seconds <= 0) return;
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** 尝试从玩家扣除传送费用，返回是否成功。 */
    private boolean payCost(Player player, double cost) {
        if (cost <= 0) return true;
        var economy = plugin.economy();
        if (economy == null) return true;
        var withdraw = economy.withdrawPlayer(player, cost);
        return withdraw.transactionSuccess();
    }

    /**
     * 收取「新建玩家地标」费用，返回是否允许继续创建。
     *
     * <p>由 {@code modules.pwarp.create-cost.currency} 选择货币：{@code money}（Vault 金币）
     * 或 {@code points}（PlayerPoints 点券）；金额为 0 或对应前置缺失时不收费。
     */
    private boolean chargeCreateCost(Player player) {
        String currency = plugin.getConfig().getString("modules.pwarp.create-cost.currency", "money");
        if ("points".equalsIgnoreCase(currency)) {
            int amount = Math.max(0, plugin.getConfig().getInt("modules.pwarp.create-cost.points", 0));
            if (amount <= 0 || !plugin.points().available()) {
                return true;
            }
            if (!plugin.points().take(player.getUniqueId(), amount)) {
                plugin.messages().send(player, "pwarp-create-insufficient-points",
                        Map.of("points", Integer.toString(amount)));
                return false;
            }
            plugin.messages().send(player, "pwarp-create-charged-points",
                    Map.of("points", Integer.toString(amount)));
            return true;
        }
        double amount = Math.max(0, plugin.getConfig().getDouble("modules.pwarp.create-cost.money", 0));
        if (amount <= 0) {
            return true;
        }
        var economy = plugin.economy();
        if (economy == null) {
            return true;
        }
        if (!economy.withdrawPlayer(player, amount).transactionSuccess()) {
            plugin.messages().send(player, "pwarp-create-insufficient-funds",
                    Map.of("cost", String.format("%.2f", amount)));
            return false;
        }
        plugin.messages().send(player, "pwarp-create-charged",
                Map.of("cost", String.format("%.2f", amount)));
        return true;
    }

    /** 基于现有 PlayerWarp 复制全部字段的可变构造器。 */
    private static final class PlayerWarpBuilder {
        private final java.util.UUID id;
        private final java.util.UUID ownerId;
        private final String ownerName;
        private String name;
        private String server;
        private String serverId;
        private String world;
        private String worldAlias;
        private double x, y, z;
        private float yaw, pitch;
        private String description;
        private String iconMaterial;
        private Integer iconCustomData;
        private String iconItemModel;
        private double cost;
        private final long createdAt;

        PlayerWarpBuilder(PlayerWarp warp) {
            this.id = warp.id();
            this.ownerId = warp.ownerId();
            this.ownerName = warp.ownerName();
            this.name = warp.name();
            this.server = warp.server();
            this.serverId = warp.serverId();
            this.world = warp.world();
            this.worldAlias = warp.worldAlias();
            this.x = warp.x();
            this.y = warp.y();
            this.z = warp.z();
            this.yaw = warp.yaw();
            this.pitch = warp.pitch();
            this.description = warp.description();
            this.iconMaterial = warp.iconMaterial();
            this.iconCustomData = warp.iconCustomData();
            this.iconItemModel = warp.iconItemModel();
            this.cost = warp.cost();
            this.createdAt = warp.createdAt();
        }

        PlayerWarpBuilder name(String v) { this.name = v; return this; }
        PlayerWarpBuilder server(String v) { this.server = v; return this; }
        PlayerWarpBuilder serverId(String v) { this.serverId = v; return this; }
        PlayerWarpBuilder world(String v) { this.world = v; return this; }
        PlayerWarpBuilder worldAlias(String v) { this.worldAlias = v; return this; }
        PlayerWarpBuilder position(double x, double y, double z, float yaw, float pitch) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; return this;
        }
        PlayerWarpBuilder description(String v) { this.description = v; return this; }
        PlayerWarpBuilder iconMaterial(String v) { this.iconMaterial = v; return this; }
        PlayerWarpBuilder iconCustomData(Integer v) { this.iconCustomData = v; return this; }
        PlayerWarpBuilder iconItemModel(String v) { this.iconItemModel = v; return this; }
        PlayerWarpBuilder cost(double v) { this.cost = v; return this; }

        PlayerWarp build() {
            return new PlayerWarp(id, ownerId, ownerName, name, server, serverId, world, worldAlias, x, y, z, yaw, pitch,
                    description, iconMaterial, iconCustomData, iconItemModel, cost, createdAt);
        }
    }
}
