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
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    /** 创建绑定插件服务的玩家地标服务。 */
    public PlayerWarpService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 执行 /pwarp <名称>，检查冷却和费用后传送；创建者本人免费。 */
    public void warp(Player player, String name) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "pwarp-not-found", Map.of("name", name));
            return;
        }
        long remaining = cooldownRemaining(player, warp);
        if (remaining > 0L) {
            plugin.messages().send(player, "pwarp-cooldown", Map.of("seconds", Long.toString(remaining)));
            return;
        }
        boolean isOwner = warp.ownerId().equals(player.getUniqueId());
        double cost = isOwner ? 0 : warp.cost();
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

    /** 玩家创建地标，检查数量上限后写入。 */
    public boolean create(Player player, String name) {
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
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        long now = System.currentTimeMillis();
        PlayerWarp warp = new PlayerWarp(
                UUID.randomUUID(),
                player.getUniqueId(),
                player.getName(),
                name, server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                "",
                "",
                null,
                "",
                plugin.getConfig().getDouble("modules.pwarp.default-cost", 0),
                plugin.getConfig().getInt("modules.pwarp.default-cooldown", 0),
                now);
        plugin.playerWarpStore().save(warp);
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

    /** 更新地标冷却秒数。 */
    public void setCooldown(Player player, String name, int cooldownSeconds) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) return;
        if (!canEdit(player, warp)) {
            plugin.messages().send(player, "pwarp-no-edit-permission");
            return;
        }
        plugin.playerWarpStore().save(build(warp, b -> b.cooldownSeconds(Math.max(0, cooldownSeconds))));
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

    /** 记录某玩家对某地标的评分（1-5 星）。 */
    public void rate(Player player, String name, int stars) {
        PlayerWarp warp = plugin.playerWarpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "pwarp-not-found", Map.of("name", name));
            return;
        }
        if (stars < 1 || stars > 5) {
            plugin.messages().send(player, "pwarp-rate-invalid");
            return;
        }
        plugin.warpRatingStore().rate(player.getUniqueId(), warp.id(), stars);
        plugin.messages().send(player, "pwarp-rated", Map.of("name", name, "stars", Integer.toString(stars)));
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
    public int maxWarps(Player player) {
        int max = plugin.getConfig().getInt("modules.pwarp.default-amount", 1);
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
        Location target = new Location(
                Bukkit.getWorld(warp.world()),
                warp.x(), warp.y(), warp.z(),
                warp.yaw(), warp.pitch());
        if (target.getWorld() == null) {
            plugin.messages().send(player, "pwarp-world-unloaded", Map.of("name", warp.name()));
            return;
        }
        plugin.back().recordLocation(player);
        startCooldown(player, warp);
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
                if (!isOwner && cost > 0) {
                    settleIncome(warp, cost);
                }
            });
        });
    }

    /** 跨服传送并在落点后结算传送收入给创建者。 */
    private void teleportCrossServer(Player player, PlayerWarp warp, boolean isOwner, double cost) {
        plugin.back().recordLocation(player);
        startCooldown(player, warp);
        org.katacr.katpa.model.LocationRecord loc = new org.katacr.katpa.model.LocationRecord(
                warp.server(), warp.world(), warp.x(), warp.y(), warp.z(),
                warp.yaw(), warp.pitch(), System.currentTimeMillis());
        if (!plugin.network().backRequest(player, warp.server(), loc, success -> {
            if (Boolean.TRUE.equals(success) && !isOwner && cost > 0) {
                settleIncome(warp, cost);
            } else if (!Boolean.TRUE.equals(success)) {
                plugin.messages().send(player, "pwarp-failed",
                        Map.of("reason", plugin.messages().text("network-reason.connect-failed")));
            }
        })) {
            plugin.messages().send(player, "proxy-unavailable");
        }
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

    /** 返回玩家对指定地标的冷却剩余秒数，0 表示可用。 */
    private long cooldownRemaining(Player player, PlayerWarp warp) {
        if (warp.cooldownSeconds() <= 0) return 0L;
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return 0L;
        Long until = map.get(warp.name().toLowerCase(java.util.Locale.ROOT));
        if (until == null) return 0L;
        long remaining = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, remaining);
    }

    /** 记录玩家对指定地标的冷却起点。 */
    private void startCooldown(Player player, PlayerWarp warp) {
        if (warp.cooldownSeconds() <= 0) return;
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(warp.name().toLowerCase(java.util.Locale.ROOT),
                        System.currentTimeMillis() + warp.cooldownSeconds() * 1000L);
    }

    /** 尝试从玩家扣除传送费用，返回是否成功。 */
    private boolean payCost(Player player, double cost) {
        if (cost <= 0) return true;
        var economy = plugin.economy();
        if (economy == null) return true;
        var withdraw = economy.withdrawPlayer(player, cost);
        return withdraw.transactionSuccess();
    }

    /** 基于现有 PlayerWarp 复制全部字段的可变构造器。 */
    private static final class PlayerWarpBuilder {
        private final java.util.UUID id;
        private final java.util.UUID ownerId;
        private final String ownerName;
        private String name;
        private String server;
        private String world;
        private double x, y, z;
        private float yaw, pitch;
        private String description;
        private String iconMaterial;
        private Integer iconCustomData;
        private String iconItemModel;
        private double cost;
        private int cooldownSeconds;
        private final long createdAt;

        PlayerWarpBuilder(PlayerWarp warp) {
            this.id = warp.id();
            this.ownerId = warp.ownerId();
            this.ownerName = warp.ownerName();
            this.name = warp.name();
            this.server = warp.server();
            this.world = warp.world();
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
            this.cooldownSeconds = warp.cooldownSeconds();
            this.createdAt = warp.createdAt();
        }

        PlayerWarpBuilder name(String v) { this.name = v; return this; }
        PlayerWarpBuilder description(String v) { this.description = v; return this; }
        PlayerWarpBuilder iconMaterial(String v) { this.iconMaterial = v; return this; }
        PlayerWarpBuilder iconCustomData(Integer v) { this.iconCustomData = v; return this; }
        PlayerWarpBuilder iconItemModel(String v) { this.iconItemModel = v; return this; }
        PlayerWarpBuilder cost(double v) { this.cost = v; return this; }
        PlayerWarpBuilder cooldownSeconds(int v) { this.cooldownSeconds = v; return this; }

        PlayerWarp build() {
            return new PlayerWarp(id, ownerId, ownerName, name, server, world, x, y, z, yaw, pitch,
                    description, iconMaterial, iconCustomData, iconItemModel, cost, cooldownSeconds, createdAt);
        }
    }
}
