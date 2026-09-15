package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.Home;
import org.katacr.katpa.model.LocationRecord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 管理玩家个人家位置传送，包含数量限制和跨服协调。 */
public final class HomeService {
    private final KaTpaPlugin plugin;
    /** 每个玩家最近一次自动设置“重生点”家的床方块标识（世界:x:y:z），用于右键床去重。 */
    private final Map<UUID, String> lastBedHomes = new ConcurrentHashMap<>();

    /** 创建绑定插件服务的家位置服务。 */
    public HomeService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 根据权限 katpa.home.amount.&lt;n&gt; 返回玩家可设置的家数量上限，无权限时取配置默认值。 */
    public int maxHomes(Player player) {
        int max = plugin.getConfig().getInt("modules.home.default-amount", 1);
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
        String currentServer = plugin.network().serverId();
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
        int maxLen = plugin.getConfig().getInt("modules.home.name-max-length", 32);
        if (name.length() > maxLen) {
            plugin.messages().send(player, "home-name-too-long", Map.of("max", Integer.toString(maxLen)));
            return false;
        }
        Home existing = plugin.homeStore().find(player.getUniqueId(), name);
        if (existing == null && plugin.homeStore().count(player.getUniqueId()) >= maxHomes(player)) {
            plugin.messages().send(player, "home-limit", Map.of("max", Integer.toString(maxHomes(player))));
            return false;
        }
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        long now = System.currentTimeMillis();
        Home home = new Home(
                existing != null ? existing.id() : java.util.UUID.randomUUID(),
                player.getUniqueId(), name, server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                existing != null ? existing.description() : "",
                existing != null ? existing.iconMaterial() : "",
                existing != null ? existing.iconCustomData() : null,
                existing != null ? existing.iconItemModel() : "",
                existing != null ? existing.createdAt() : now);
        plugin.homeStore().save(home);
        plugin.messages().send(player, existing != null ? "home-updated" : "home-created",
                Map.of("name", name));
        return true;
    }

    /** 右键床自动设置“重生点”家：同一张床首次点击才更新，重复点击同一张床跳过（内存去重，重启后失效可接受）。 */
    public boolean setBedHome(Player player, String name, org.bukkit.block.Block bed) {
        String bedKey = bedKey(bed);
        if (bedKey == null) {
            return false;
        }
        if (bedKey.equals(lastBedHomes.get(player.getUniqueId()))) {
            return false;
        }
        if (!setHome(player, name)) {
            return false;
        }
        lastBedHomes.put(player.getUniqueId(), bedKey);
        return true;
    }

    /** 计算整张床的统一标识：以“床头”（HEAD）所在格的世界与坐标为准，头/脚两格归一到同一个 key。 */
    private static String bedKey(org.bukkit.block.Block bed) {
        org.bukkit.block.data.type.Bed data;
        try {
            if (!(bed.getBlockData() instanceof org.bukkit.block.data.type.Bed bedData)) {
                return null;
            }
            data = bedData;
        } catch (Exception ignored) {
            return null;
        }
        org.bukkit.block.Block head = data.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD
                ? bed
                : bed.getRelative(data.getFacing());
        return head.getWorld().getName() + ":" + head.getX() + ":" + head.getY() + ":" + head.getZ();
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

    /** 更新家的位置为玩家当前位置（保留描述与图标），返回是否成功。 */
    public boolean updateLocation(Player player, String name) {
        Home home = plugin.homeStore().find(player.getUniqueId(), name);
        if (home == null) {
            plugin.messages().send(player, "home-not-found", Map.of("name", name));
            return false;
        }
        Location loc = player.getLocation();
        String server = plugin.network().serverId();
        Home updated = new Home(home.id(), home.ownerId(), home.name(), server, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                home.description(), home.iconMaterial(), home.iconCustomData(), home.iconItemModel(),
                home.createdAt());
        plugin.homeStore().save(updated);
        plugin.messages().send(player, "home-updated", Map.of("name", name));
        return true;
    }

    /** 更新家的描述。 */
    public void setDescription(UUID ownerId, String name, String description) {
        Home home = plugin.homeStore().find(ownerId, name);
        if (home == null) return;
        int maxLen = plugin.getConfig().getInt("modules.home.description-max-length", 100);
        if (description.length() > maxLen) {
            return;
        }
        plugin.homeStore().save(build(home, b -> b.description(description)));
    }

    /** 重命名家，返回是否成功。 */
    public boolean rename(UUID ownerId, String oldName, String newName) {
        Home home = plugin.homeStore().find(ownerId, oldName);
        if (home == null) return false;
        int maxLen = plugin.getConfig().getInt("modules.home.name-max-length", 32);
        if (newName.length() > maxLen) return false;
        if (plugin.homeStore().find(ownerId, newName) != null) return false;
        plugin.homeStore().remove(ownerId, oldName);
        plugin.homeStore().save(build(home, b -> b.name(newName)));
        return true;
    }

    /** 以玩家手中物品设置家图标。 */
    public void setIcon(UUID ownerId, String name, org.bukkit.inventory.ItemStack item) {
        Home home = plugin.homeStore().find(ownerId, name);
        if (home == null) return;
        String material = item.getType().name();
        Integer customData = item.getItemMeta() != null && item.getItemMeta().hasCustomModelData()
                ? item.getItemMeta().getCustomModelData() : null;
        String itemModel = org.katacr.katpa.ui.inventory.gui.KaTpaGuiListProvider.readItemModelReflect(item.getItemMeta());
        plugin.homeStore().save(build(home, b -> b.iconMaterial(material)
                .iconCustomData(customData).iconItemModel(itemModel == null ? "" : itemModel)));
    }

    /** 基于现有 Home 复制字段并应用修改。 */
    private Home build(Home home, java.util.function.UnaryOperator<HomeBuilder> fn) {
        return fn.apply(new HomeBuilder(home)).build();
    }

    /** 同服家传送。 */
    private void teleportLocal(Player player, Home home) {
        if (!plugin.teleports().ensureTargetAvailable(player, home.server(), home.world(),
                "home-world-unloaded", Map.of("name", home.name()))) {
            return;
        }
        Location target = new Location(
                Bukkit.getWorld(home.world()),
                home.x(), home.y(), home.z(),
                home.yaw(), home.pitch());
        plugin.back().recordLocation(player);
        plugin.teleports().beginDirect(player, "home", () -> {
            plugin.back().markOwnTeleport(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.teleport(target)) {
                    plugin.messages().send(player, "teleport-failed");
                    return;
                }
                plugin.sounds().playAt(target, "teleport", "home");
                plugin.messages().sendActionBar(player,
                        plugin.messages().component("home-success", Map.of("name", home.name()), false));
            });
        });
    }

    /** 跨服家传送：先完成源服吟唱，再请求代理切服并在目标服落点。 */
    private void teleportCrossServer(Player player, Home home) {
        if (!plugin.teleports().ensureTargetAvailable(player, home.server(), null, null, null)) {
            return;
        }
        plugin.back().recordLocation(player);
        LocationRecord loc = new LocationRecord(
                home.server(), home.world(), home.x(), home.y(), home.z(),
                home.yaw(), home.pitch(), System.currentTimeMillis());
        plugin.teleports().beginDirect(player, "home", () -> {
            if (!plugin.network().backRequest(player, home.server(), loc, success -> {
                if (!Boolean.TRUE.equals(success)) {
                    plugin.messages().send(player, "home-failed",
                            Map.of("reason", plugin.messages().text("network-reason.connect-failed")));
                }
            })) {
                plugin.messages().send(player, "proxy-unavailable");
            }
        });
    }

    /** 基于现有 Home 复制全部字段的可变构造器。 */
    private static final class HomeBuilder {
        private final java.util.UUID id;
        private final java.util.UUID ownerId;
        private String name;
        private final String server;
        private final String world;
        private final double x, y, z;
        private final float yaw, pitch;
        private String description;
        private String iconMaterial;
        private Integer iconCustomData;
        private String iconItemModel;
        private final long createdAt;

        HomeBuilder(Home home) {
            this.id = home.id();
            this.ownerId = home.ownerId();
            this.name = home.name();
            this.server = home.server();
            this.world = home.world();
            this.x = home.x();
            this.y = home.y();
            this.z = home.z();
            this.yaw = home.yaw();
            this.pitch = home.pitch();
            this.description = home.description();
            this.iconMaterial = home.iconMaterial();
            this.iconCustomData = home.iconCustomData();
            this.iconItemModel = home.iconItemModel();
            this.createdAt = home.createdAt();
        }

        HomeBuilder name(String v) { this.name = v; return this; }
        HomeBuilder description(String v) { this.description = v; return this; }
        HomeBuilder iconMaterial(String v) { this.iconMaterial = v; return this; }
        HomeBuilder iconCustomData(Integer v) { this.iconCustomData = v; return this; }
        HomeBuilder iconItemModel(String v) { this.iconItemModel = v; return this; }

        Home build() {
            return new Home(id, ownerId, name, server, world, x, y, z, yaw, pitch,
                    description, iconMaterial, iconCustomData, iconItemModel, createdAt);
        }
    }
}
