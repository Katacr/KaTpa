package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.Home;
import org.katacr.katpa.model.LocationRecord;

import java.util.Map;

/** 管理玩家个人家位置传送，包含数量限制和跨服协调。 */
public final class HomeService {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的家位置服务。 */
    public HomeService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 根据权限 katpa.home.amount.&lt;n&gt; 返回玩家可设置的家数量上限，默认 1。 */
    public int maxHomes(Player player) {
        int max = 1;
        for (var perm : player.getEffectivePermissions()) {
            String prefix = "katpa.home.amount.";
            if (perm.getValue() && perm.getPermission().startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.getPermission().substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    /** 执行 /home <名称>，检查存在性后传送。 */
    public void home(Player player, String name) {
        Home home = plugin.homeStore().find(player.getUniqueId(), name);
        if (home == null) {
            plugin.messages().send(player, "home-not-found", Map.of("name", name));
            return;
        }
        if (plugin.teleports().isBusy(player.getUniqueId())) {
            plugin.messages().send(player, "teleport-busy");
            return;
        }
        String currentServer = plugin.network().enabled()
                ? plugin.getConfig().getString("server-id", "local")
                : "local";
        if (home.server().equals(currentServer) || !plugin.network().enabled()) {
            teleportLocal(player, home);
            return;
        }
        if (!plugin.network().available()) {
            plugin.messages().send(player, "proxy-unavailable");
            return;
        }
        teleportCrossServer(player, home);
    }

    /** 玩家创建或更新家位置。 */
    public boolean setHome(Player player, String name) {
        if (name.isBlank()) {
            plugin.messages().send(player, "home-name-empty");
            return false;
        }
        Home existing = plugin.homeStore().find(player.getUniqueId(), name);
        if (existing == null && plugin.homeStore().count(player.getUniqueId()) >= maxHomes(player)) {
            plugin.messages().send(player, "home-limit", Map.of("max", Integer.toString(maxHomes(player))));
            return false;
        }
        Location loc = player.getLocation();
        String server = plugin.network().enabled()
                ? plugin.getConfig().getString("server-id", "local")
                : "local";
        Home home = new Home(player.getUniqueId(), name, server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                System.currentTimeMillis());
        plugin.homeStore().save(home);
        plugin.messages().send(player, existing != null ? "home-updated" : "home-created",
                Map.of("name", name));
        return true;
    }

    /** 玩家删除家位置。 */
    public boolean delHome(Player player, String name) {
        Home home = plugin.homeStore().find(player.getUniqueId(), name);
        if (home == null) {
            plugin.messages().send(player, "home-not-found", Map.of("name", name));
            return false;
        }
        plugin.homeStore().remove(player.getUniqueId(), name);
        plugin.messages().send(player, "home-deleted", Map.of("name", name));
        return true;
    }

    /** 同服家传送。 */
    private void teleportLocal(Player player, Home home) {
        Location target = new Location(
                Bukkit.getWorld(home.world()),
                home.x(), home.y(), home.z(),
                home.yaw(), home.pitch());
        if (target.getWorld() == null) {
            plugin.messages().send(player, "home-world-unloaded", Map.of("name", home.name()));
            return;
        }
        plugin.back().recordLocation(player);
        plugin.back().markOwnTeleport(player.getUniqueId());
        player.teleportAsync(target).whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !Boolean.TRUE.equals(success)) {
                plugin.messages().send(player, "teleport-failed");
                return;
            }
            plugin.sounds().playAt(target, "teleport");
            plugin.messages().sendActionBar(player,
                    plugin.messages().component("home-success", Map.of("name", home.name()), false));
        }));
    }

    /** 跨服家传送：通过 KaProxy 切服后在目标服落点。 */
    private void teleportCrossServer(Player player, Home home) {
        plugin.back().recordLocation(player);
        LocationRecord loc = new LocationRecord(
                home.server(), home.world(), home.x(), home.y(), home.z(),
                home.yaw(), home.pitch(), System.currentTimeMillis());
        if (!plugin.network().backRequest(player, home.server(), loc, success -> {
            if (!Boolean.TRUE.equals(success)) {
                plugin.messages().send(player, "home-failed",
                        Map.of("reason", plugin.messages().text("network-reason.connect-failed")));
            }
        })) {
            plugin.messages().send(player, "proxy-unavailable");
        }
    }
}
