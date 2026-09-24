package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.LocationRecord;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** 管理 /back 上次位置记录、同服传送和跨服返回协调。 */
public final class BackService {
    private final KaTpaPlugin plugin;
    private final ConcurrentMap<UUID, Boolean> pendingBack = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> ownTeleport = new ConcurrentHashMap<>();

    /** 创建绑定插件服务的返回位置服务。 */
    public BackService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 标记下一次本插件的 PLUGIN 传送事件应被忽略，避免覆盖返回起点。 */
    public void markOwnTeleport(UUID playerId) {
        ownTeleport.put(playerId, true);
    }

    /** 返回并消费本插件传送标记，供监听器判断是否跳过记录。 */
    public boolean isOwnTeleport(UUID playerId) {
        return ownTeleport.remove(playerId) != null;
    }

    /** 记录玩家当前位置为上次位置，在传送、死亡和离线前调用。当前世界在 back 黑名单时不记录。 */
    public void recordLocation(Player player) {
        if (plugin.teleports().isCurrentWorldDisabled("back", player)) {
            return;
        }
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        LocationRecord record = new LocationRecord(
                server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                System.currentTimeMillis());
        plugin.backStore().setLastLocation(player.getUniqueId(), record);
    }

    /** 执行 /back 指令，同服直接传送，跨服通过代理。 */
    public void back(Player player) {
        LocationRecord record = plugin.backStore().lastLocation(player.getUniqueId());
        if (record == null) {
            plugin.messages().send(player, "back-none");
            return;
        }
        if (plugin.teleports().isBusy(player.getUniqueId())) {
            plugin.messages().send(player, "teleport-busy");
            return;
        }
        String currentServer = plugin.network().serverId();
        if (record.server().equals(currentServer) || !plugin.network().enabled()) {
            teleportLocal(player, record);
            return;
        }
        if (!plugin.network().available()) {
            plugin.messages().send(player, "proxy-unavailable");
            return;
        }
        teleportCrossServer(player, record);
    }

    /** 代理将玩家切服后由目标服调用，完成最终落点传送。 */
    public void handleArrival(Player player, LocationRecord record) {
        handleArrival(player, record, "back");
    }

    /** 代理将玩家切服后由目标服调用，完成最终落点传送，并按动作类型触发对应事件。 */
    public void handleArrival(Player player, LocationRecord record, String actionType) {
        if (!player.isOnline()) {
            return;
        }
        Location target = new Location(
                Bukkit.getWorld(record.world()),
                record.x(), record.y(), record.z(),
                record.yaw(), record.pitch());
        if (target.getWorld() == null) {
            plugin.messages().send(player, "back-world-unloaded");
            plugin.network().backArrivalFailed(player, "world-unloaded");
            return;
        }
        markOwnTeleport(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingBack.remove(player.getUniqueId());
            if (!player.teleport(target)) {
                plugin.messages().send(player, "teleport-failed");
                plugin.network().backArrivalFailed(player, "teleport-failed");
                return;
            }
            plugin.sounds().playAt(target, "teleport", "back");
            plugin.messages().sendActionBar(player,
                    plugin.messages().component("back-success", java.util.Map.of(), false));
            plugin.network().backArrivalComplete(player);
            org.bukkit.Bukkit.getPluginManager().callEvent(
                    new org.katacr.katpa.api.event.KaTpaEvent(player, resolveArrivalAction(actionType), null));
        });
    }

    /** 把跨服动作类型字符串映射为 KaTpaEvent.Action。 */
    private static org.katacr.katpa.api.event.KaTpaEvent.Action resolveArrivalAction(String actionType) {
        if (actionType == null) {
            return org.katacr.katpa.api.event.KaTpaEvent.Action.BACK;
        }
        return switch (actionType.toLowerCase(java.util.Locale.ROOT)) {
            case "home" -> org.katacr.katpa.api.event.KaTpaEvent.Action.HOME;
            case "warp", "player_warp" -> org.katacr.katpa.api.event.KaTpaEvent.Action.WARP;
            case "dback" -> org.katacr.katpa.api.event.KaTpaEvent.Action.DBACK;
            default -> org.katacr.katpa.api.event.KaTpaEvent.Action.BACK;
        };
    }

    /** 玩家进入子服时检查是否有待执行的跨服返回。 */
    public void checkPending(UUID playerId) {
        pendingBack.remove(playerId);
    }

    /** 同服直接传送。 */
    private void teleportLocal(Player player, LocationRecord record) {
        if (!plugin.teleports().ensureTargetAvailable("back", player, record.server(), record.server(),
                record.world(), "back-world-unloaded", java.util.Map.of())) {
            return;
        }
        Location target = new Location(
                Bukkit.getWorld(record.world()),
                record.x(), record.y(), record.z(),
                record.yaw(), record.pitch());
        recordLocation(player);
        plugin.teleports().beginDirect(player, "back", () -> {
            markOwnTeleport(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.teleport(target)) {
                    plugin.messages().send(player, "teleport-failed");
                    return;
                }
                plugin.sounds().playAt(target, "teleport", "back");
                plugin.messages().sendActionBar(player,
                        plugin.messages().component("back-success", java.util.Map.of(), false));
                org.bukkit.Bukkit.getPluginManager().callEvent(
                        new org.katacr.katpa.api.event.KaTpaEvent(player, org.katacr.katpa.api.event.KaTpaEvent.Action.BACK, null));
            });
        });
    }

    /** 跨服返回：先记录当前位置，完成源服吟唱后请求代理切服并在目标服落点。 */
    private void teleportCrossServer(Player player, LocationRecord record) {
        if (!plugin.teleports().ensureTargetAvailable("back", player, record.server(), record.server(),
                record.world(), null, null)) {
            return;
        }
        recordLocation(player);
        pendingBack.put(player.getUniqueId(), true);
        plugin.teleports().beginDirect(player, "back", () -> {
            if (!plugin.network().backRequest(player, record.server(), record, success -> {
                if (!Boolean.TRUE.equals(success)) {
                    pendingBack.remove(player.getUniqueId());
                    plugin.messages().send(player, "back-failed",
                            java.util.Map.of("reason", plugin.messages().text("network-reason.connect-failed")));
                }
            })) {
                pendingBack.remove(player.getUniqueId());
                plugin.messages().send(player, "proxy-unavailable");
            }
        });
    }
}
