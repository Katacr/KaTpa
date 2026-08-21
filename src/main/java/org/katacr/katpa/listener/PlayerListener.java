package org.katacr.katpa.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
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
        plugin.back().checkPending(event.getPlayer().getUniqueId());
        try {
            plugin.backStore().refresh(event.getPlayer().getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("刷新玩家返回位置失败: " + e.getMessage());
        }
        try {
            plugin.homeStore().load(event.getPlayer().getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("加载玩家家位置失败: " + e.getMessage());
        }
    }

    /** 玩家离开服务器时取消其相关请求和吟唱，并记录上次位置供跨服返回。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.back().recordLocation(event.getPlayer());
        plugin.requests().cancelForPlayer(event.getPlayer().getUniqueId());
        plugin.teleports().cancelForPlayer(event.getPlayer().getUniqueId());
    }

    /** 玩家实际改变坐标时尝试中断传送吟唱，并在传送前记录上次位置。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) {
            plugin.teleports().handleMove(event.getPlayer(), event.getTo());
        }
    }

    /** 任何插件或指令传送前记录出发位置，排除本插件自身的传送避免覆盖。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && plugin.back().isOwnTeleport(event.getPlayer().getUniqueId())) {
            return;
        }
        plugin.back().recordLocation(event.getPlayer());
    }

    /** 玩家死亡时按权限槽位记录死亡位置。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.dback().recordDeath(event.getEntity());
    }

    /** 玩家受到有效伤害时中断传送吟唱。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.teleports().handleDamage(player);
}

    }

    /** 玩家开始潜行时交给双击潜行请求状态机处理。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            plugin.requests().handleSneak(event.getPlayer());
        }
    }
}
