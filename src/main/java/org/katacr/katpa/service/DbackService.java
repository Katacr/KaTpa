package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.LocationRecord;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** 管理 /dback 死亡位置记录、权限槽位和跨服返回传送。 */
public final class DbackService {
    private final KaTpaPlugin plugin;
    private final ConcurrentMap<UUID, Boolean> pendingDback = new ConcurrentHashMap<>();

    /** 创建绑定插件服务的死亡位置服务。 */
    public DbackService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 玩家死亡时记录死亡位置（只保留最近一次），并发送可点击的返回提示；黑名单世界不记录。 */
    public void recordDeath(Player player) {
        if (plugin.teleports().isCurrentWorldDisabled("dback", player)) {
            return;
        }
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        LocationRecord record = new LocationRecord(
                server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                System.currentTimeMillis());
        plugin.backStore().setDeathLocation(player.getUniqueId(), record);
        sendDeathMessage(player, loc);
    }

    /** 发送可点击的死亡位置提示，点击后传送到该死亡位置（等价 /dback）。 */
    private void sendDeathMessage(Player player, Location loc) {
        String text = plugin.messages().text("dback-death-message", Map.of(
                "world", org.katacr.katpa.util.WorldNames.display(loc.getWorld().getName()),
                "x", Integer.toString(loc.getBlockX()),
                "y", Integer.toString(loc.getBlockY()),
                "z", Integer.toString(loc.getBlockZ())));
        org.katacr.katpa.text.ClickableText.sendClickable(player, text,
                this::dback, java.time.Duration.ofSeconds(60));
    }

    /** 返回玩家最近一次死亡位置，无记录时返回 null。 */
    public LocationRecord deathLocation(Player player) {
        return plugin.backStore().deathLocation(player.getUniqueId());
    }

    /** 执行 /dback，同服直接传送，跨服通过代理。 */
    public void dback(Player player) {
        LocationRecord record = deathLocation(player);
        if (record == null) {
            plugin.messages().send(player, "dback-no-location");
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
        // 跨服：先校验目标子服可用，再在本服完成吟唱后请求代理切服（与 /back 行为对齐）
        if (!plugin.teleports().ensureTargetAvailable("dback", player, record.server(), record.server(),
                record.world(), null, null)) {
            return;
        }
        plugin.back().recordLocation(player);
        pendingDback.put(player.getUniqueId(), true);
        plugin.teleports().beginDirect(player, "dback", () -> {
            if (!plugin.network().backRequest(player, record.server(), record, "dback", success -> {
                if (!Boolean.TRUE.equals(success)) {
                    pendingDback.remove(player.getUniqueId());
                    plugin.messages().send(player, "back-failed",
                            Map.of("reason", plugin.messages().text("network-reason.connect-failed")));
                }
            })) {
                pendingDback.remove(player.getUniqueId());
                plugin.messages().send(player, "proxy-unavailable");
            }
        });
    }

    /** 同服直接传送。 */
    private void teleportLocal(Player player, LocationRecord record) {
        if (!plugin.teleports().ensureTargetAvailable("dback", player, record.server(), record.server(),
                record.world(), "back-world-unloaded", Map.of())) {
            return;
        }
        Location target = new Location(
                Bukkit.getWorld(record.world()),
                record.x(), record.y(), record.z(),
                record.yaw(), record.pitch());
        plugin.back().recordLocation(player);
        plugin.teleports().beginDirect(player, "dback", () -> {
            plugin.back().markOwnTeleport(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.teleport(target)) {
                    plugin.messages().send(player, "teleport-failed");
                    return;
                }
                plugin.sounds().playAt(target, "teleport", "dback");
                plugin.messages().sendActionBar(player,
                        plugin.messages().component("dback-success", Map.of(), false));
                org.bukkit.Bukkit.getPluginManager().callEvent(
                        new org.katacr.katpa.api.event.KaTpaEvent(player, org.katacr.katpa.api.event.KaTpaEvent.Action.DBACK, null));
            });
        });
    }
}
