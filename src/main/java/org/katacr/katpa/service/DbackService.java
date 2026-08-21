package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.LocationRecord;

import java.util.List;
import java.util.Map;

/** 管理 /dback 死亡位置记录、权限槽位和跨服返回传送。 */
public final class DbackService {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的死亡位置服务。 */
    public DbackService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 根据权限 katpa.dback.amount.&lt;n&gt; 返回玩家可用的死亡位置个数，无权限时取配置默认值。 */
    public int maxSlots(Player player) {
        int max = plugin.getConfig().getInt("modules.dback.default-amount", 1);
        for (var perm : player.getEffectivePermissions()) {
            String prefix = "katpa.dback.amount.";
            if (perm.getValue() && perm.getPermission().startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.getPermission().substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    /** 玩家死亡时记录死亡位置，按权限槽位滚动。 */
    public void recordDeath(Player player) {
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        LocationRecord record = new LocationRecord(
                server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                System.currentTimeMillis());
        plugin.backStore().addDeathLocation(player.getUniqueId(), record, maxSlots(player));
    }

    /** 返回玩家全部死亡位置（0 为最近一次）。 */
    public List<LocationRecord> deathLocations(Player player) {
        return plugin.backStore().deathLocations(player.getUniqueId());
    }

    /** 执行 /dback [序号]，同服直接传送，跨服通过代理。 */
    public void dback(Player player, int slot) {
        List<LocationRecord> records = deathLocations(player);
        if (slot < 1 || slot > records.size()) {
            plugin.messages().send(player, "dback-slot-missing",
                    Map.of("count", Integer.toString(records.size())));
            return;
        }
        LocationRecord record = records.get(slot - 1);
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
        plugin.back().recordLocation(player);
        if (!plugin.network().backRequest(player, record.server(), record, success -> {
            if (!Boolean.TRUE.equals(success)) {
                plugin.messages().send(player, "back-failed",
                        Map.of("reason", plugin.messages().text("network-reason.connect-failed")));
            }
        })) {
            plugin.messages().send(player, "proxy-unavailable");
        }
    }

    /** 同服直接传送。 */
    private void teleportLocal(Player player, LocationRecord record) {
        Location target = new Location(
                Bukkit.getWorld(record.world()),
                record.x(), record.y(), record.z(),
                record.yaw(), record.pitch());
        if (target.getWorld() == null) {
            plugin.messages().send(player, "back-world-unloaded");
            return;
        }
        plugin.back().recordLocation(player);
        plugin.teleports().beginDirect(player, "dback", () -> {
            plugin.back().markOwnTeleport(player.getUniqueId());
            player.teleportAsync(target).whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || !Boolean.TRUE.equals(success)) {
                    plugin.messages().send(player, "teleport-failed");
                    return;
                }
                plugin.sounds().playAt(target, "teleport", "dback");
                plugin.messages().sendActionBar(player,
                        plugin.messages().component("dback-success", Map.of(), false));
            }));
        });
    }
}
