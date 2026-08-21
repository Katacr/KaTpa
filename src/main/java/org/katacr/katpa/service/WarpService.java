package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.Warp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 管理 /warp 地标传送，包含权限检查、冷却、付费和跨服协调。 */
public final class WarpService {
    private final KaTpaPlugin plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    /** 创建绑定插件服务的地标传送服务。 */
    public WarpService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 执行 /warp <名称>，检查权限、冷却和费用后传送。 */
    public void warp(Player player, String name) {
        Warp warp = plugin.warpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "warp-not-found", Map.of("name", name));
            return;
        }
        if (!warp.permission().isBlank() && !player.hasPermission(warp.permission())) {
            plugin.messages().send(player, "warp-no-permission", Map.of("name", warp.name()));
            return;
        }
        long remaining = cooldownRemaining(player, warp);
        if (remaining > 0L) {
            plugin.messages().send(player, "warp-cooldown", Map.of("seconds", Long.toString(remaining)));
            return;
        }
        if (warp.cost() > 0 && !payCost(player, warp.cost())) {
            plugin.messages().send(player, "warp-insufficient-funds", Map.of("cost", String.format("%.2f", warp.cost())));
            return;
        }
        String currentServer = plugin.network().serverId();
        if (warp.server().equals(currentServer) || !plugin.network().enabled()) {
            teleportLocal(player, warp);
            return;
        }
        if (!plugin.network().available()) {
            plugin.messages().send(player, "proxy-unavailable");
            return;
        }
        teleportCrossServer(player, warp);
    }

    /** 管理员创建或更新地标。 */
    public boolean setWarp(Player player, String name) {
        if (name.isBlank()) {
            plugin.messages().send(player, "warp-name-empty");
            return false;
        }
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        Warp existing = plugin.warpStore().find(name);
        long now = System.currentTimeMillis();
        Warp warp = new Warp(
                name, server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                existing != null ? existing.permission()
                        : plugin.getConfig().getString("modules.warp.default-permission", ""),
                existing != null ? existing.cooldownSeconds()
                        : plugin.getConfig().getInt("modules.warp.default-cooldown", 0),
                existing != null ? existing.cost()
                        : plugin.getConfig().getDouble("modules.warp.default-cost", 0),
                existing != null ? existing.createdAt() : now,
                now);
        plugin.warpStore().save(warp);
        plugin.messages().send(player, existing != null ? "warp-updated" : "warp-created",
                Map.of("name", name));
        return true;
    }

    /** 管理员删除地标。 */
    public boolean delWarp(Player player, String name) {
        Warp warp = plugin.warpStore().find(name);
        if (warp == null) {
            plugin.messages().send(player, "warp-not-found", Map.of("name", name));
            return false;
        }
        plugin.warpStore().remove(name);
        plugin.messages().send(player, "warp-deleted", Map.of("name", name));
        return true;
    }

    /** 更新地标的权限要求。 */
    public void setPermission(String name, String permission) {
        Warp warp = plugin.warpStore().find(name);
        if (warp == null) return;
        plugin.warpStore().save(new Warp(warp.name(), warp.server(), warp.world(),
                warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch(),
                permission, warp.cooldownSeconds(), warp.cost(),
                warp.createdAt(), System.currentTimeMillis()));
    }

    /** 更新地标的冷却秒数。 */
    public void setCooldown(String name, int cooldownSeconds) {
        Warp warp = plugin.warpStore().find(name);
        if (warp == null) return;
        plugin.warpStore().save(new Warp(warp.name(), warp.server(), warp.world(),
                warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch(),
                warp.permission(), Math.max(0, cooldownSeconds), warp.cost(),
                warp.createdAt(), System.currentTimeMillis()));
    }

    /** 更新地标的传送费用。 */
    public void setCost(String name, double cost) {
        Warp warp = plugin.warpStore().find(name);
        if (warp == null) return;
        plugin.warpStore().save(new Warp(warp.name(), warp.server(), warp.world(),
                warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch(),
                warp.permission(), warp.cooldownSeconds(), Math.max(0, cost),
                warp.createdAt(), System.currentTimeMillis()));
    }

    /** 更新地标的位置为执行者当前位置。 */
    public void updateLocation(Player player, String name) {
        Warp warp = plugin.warpStore().find(name);
        if (warp == null) return;
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        plugin.warpStore().save(new Warp(warp.name(), server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                warp.permission(), warp.cooldownSeconds(), warp.cost(),
                warp.createdAt(), System.currentTimeMillis()));
    }

    /** 玩家进入子服时清理其跨服 warp 待交付标记。 */
    public void checkPending(UUID playerId) {
    }

    /** 同服地标传送。 */
    private void teleportLocal(Player player, Warp warp) {
        Location target = new Location(
                Bukkit.getWorld(warp.world()),
                warp.x(), warp.y(), warp.z(),
                warp.yaw(), warp.pitch());
        if (target.getWorld() == null) {
            plugin.messages().send(player, "warp-world-unloaded", Map.of("name", warp.name()));
            return;
        }
        plugin.back().recordLocation(player);
        startCooldown(player, warp);
        plugin.teleports().beginDirect(player, "warp", () -> {
            plugin.back().markOwnTeleport(player.getUniqueId());
            player.teleportAsync(target).whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || !Boolean.TRUE.equals(success)) {
                    plugin.messages().send(player, "teleport-failed");
                    return;
                }
                plugin.sounds().playAt(target, "teleport", "warp");
                plugin.messages().sendActionBar(player,
                        plugin.messages().component("warp-success", Map.of("name", warp.name()), false));
            }));
        });
    }

    /** 跨服地标传送：通过 KaProxy 切服后在目标服落点。 */
    private void teleportCrossServer(Player player, Warp warp) {
        plugin.back().recordLocation(player);
        startCooldown(player, warp);
        org.katacr.katpa.model.LocationRecord loc = new org.katacr.katpa.model.LocationRecord(
                warp.server(), warp.world(), warp.x(), warp.y(), warp.z(),
                warp.yaw(), warp.pitch(), System.currentTimeMillis());
        if (!plugin.network().backRequest(player, warp.server(), loc, success -> {
            if (!Boolean.TRUE.equals(success)) {
                plugin.messages().send(player, "warp-failed",
                        Map.of("reason", plugin.messages().text("network-reason.connect-failed")));
            }
        })) {
            plugin.messages().send(player, "proxy-unavailable");
        }
    }

    /** 返回玩家对指定地标的冷却剩余秒数，0 表示可用。 */
    private long cooldownRemaining(Player player, Warp warp) {
        if (warp.cooldownSeconds() <= 0) return 0L;
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0L;
        Long until = playerCooldowns.get(warp.name().toLowerCase(java.util.Locale.ROOT));
        if (until == null) return 0L;
        long remaining = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, remaining);
    }

    /** 记录玩家对指定地标的冷却起点。 */
    private void startCooldown(Player player, Warp warp) {
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
}
