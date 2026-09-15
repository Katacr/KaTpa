package org.katacr.katpa.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.katacr.katpa.KaTpaPlugin;

/** 将玩家上线、离线、移动、受伤与潜行事件转交给对应服务。 */
public final class PlayerListener implements Listener {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的玩家事件监听器。 */
    public PlayerListener(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 玩家进入服务器时更新数据库中的最后名称。 */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.settings().rememberPlayer(event.getPlayer());
        plugin.network().handleJoin(event.getPlayer());
        if (plugin.moduleEnabled("back")) {
            plugin.back().checkPending(event.getPlayer().getUniqueId());
        }
        if (plugin.backStore() != null) {
            try {
                plugin.backStore().refresh(event.getPlayer().getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().warning("刷新玩家返回位置失败: " + e.getMessage());
            }
        }
        if (plugin.homeStore() != null) {
            try {
                plugin.homeStore().load(event.getPlayer().getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().warning("加载玩家家位置失败: " + e.getMessage());
            }
        }
        if (plugin.playerWarp() != null) {
            plugin.playerWarp().claimPendingIncome(event.getPlayer());
        }
    }

    /** 玩家离开服务器时取消其相关请求和吟唱，并记录上次位置供跨服返回。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.back() != null) {
            plugin.back().recordLocation(event.getPlayer());
        }
        if (plugin.requests() != null) {
            plugin.requests().cancelForPlayer(event.getPlayer().getUniqueId());
        }
        if (plugin.teleports() != null) {
            plugin.teleports().cancelForPlayer(event.getPlayer().getUniqueId());
        }
    }

    /** 玩家实际改变坐标时尝试中断传送吟唱，并在传送前记录上次位置。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.teleports() != null && event.getFrom() != null && event.getTo() != null) {
            org.bukkit.Location from = event.getFrom();
            org.bukkit.Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                plugin.teleports().handleMove(event.getPlayer(), to);
            }
        }
    }

    /** 任何插件或指令传送前记录出发位置，排除本插件自身的传送避免覆盖。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.back() == null) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && plugin.back().isOwnTeleport(event.getPlayer().getUniqueId())) {
            return;
        }
        if (isNegligibleTeleport(event)) {
            return;
        }
        plugin.back().recordLocation(event.getPlayer());
    }

    /** 同世界内传送且新位置与旧位置距离小于 modules.back.min-distance 时，视为无需更新 /back 的短距离传送。 */
    private boolean isNegligibleTeleport(PlayerTeleportEvent event) {
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        double minDistance = plugin.getConfig().getDouble("modules.back.min-distance", 16);
        if (minDistance <= 0) {
            return false;
        }
        return from.distanceSquared(to) < minDistance * minDistance;
    }

    /** 玩家死亡时按权限槽位记录死亡位置。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.dback() != null) {
            plugin.dback().recordDeath(event.getEntity());
        }
    }

    /** 玩家受到有效伤害时中断传送吟唱。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.teleports() != null) {
            plugin.teleports().handleDamage(player);
        }
    }

    /** 玩家开始潜行时交给双击潜行请求状态机处理。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && plugin.requests() != null) {
            plugin.requests().handleSneak(event.getPlayer());
        }
    }

    /** 玩家右键床时记录个人“重生点”家（白天/夜晚均可触发），已在该床记录过则跳过避免重复更新。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null || !clicked.getType().name().endsWith("_BED")) {
            return;
        }
        if (!plugin.moduleEnabled("home") || plugin.home() == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("modules.home.bed-home", true)) {
            return;
        }
        String name = plugin.getConfig().getString("modules.home.bed-home-name", "重生点");
        plugin.home().setBedHome(event.getPlayer(), name, clicked);
    }
}
