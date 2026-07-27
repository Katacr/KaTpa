package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.TeleportRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 管理吟唱倒计时、移动或伤害中断以及最终异步传送。 */
public final class TeleportService {
    private final KaTpaPlugin plugin;
    private final Map<UUID, WarmupSession> sessions = new HashMap<>();

    /** 创建绑定插件生命周期的传送服务。 */
    public TeleportService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 判断玩家当前是否正在进行传送吟唱。 */
    public boolean isBusy(UUID travelerId) {
        return sessions.containsKey(travelerId);
    }

    /** 判断两名玩家当前位置是否允许建立传送。 */
    public boolean canUse(Player traveler, Player destination, boolean notifyTraveler) {
        if (isDisabledWorld(traveler.getWorld().getName()) || isDisabledWorld(destination.getWorld().getName())) {
            if (notifyTraveler) {
                plugin.messages().send(traveler, "world-disabled");
            }
            return false;
        }
        if (!plugin.getConfig().getBoolean("allow-cross-world", true)
                && traveler.getWorld() != destination.getWorld()) {
            if (notifyTraveler) {
                plugin.messages().send(traveler, "cross-world-disabled");
            }
            return false;
        }
        return true;
    }

    /** 根据已接受请求启动实际旅行者的吟唱倒计时。 */
    public boolean begin(TeleportRequest request) {
        Player traveler = Bukkit.getPlayer(request.travelerId());
        Player destination = Bukkit.getPlayer(request.destinationId());
        if (traveler == null || destination == null) {
            Player onlinePlayer = traveler != null ? traveler : destination;
            if (onlinePlayer != null) {
                plugin.messages().sendActionBar(onlinePlayer, "teleport-cancelled-quit");
            }
            return false;
        }
        if (isBusy(traveler.getUniqueId())) {
            plugin.messages().send(traveler, "teleport-busy");
            return false;
        }
        if (!canUse(traveler, destination, true)) {
            return false;
        }

        int warmupSeconds = Math.max(0, plugin.getConfig().getInt("warmup-seconds", 5));
        WarmupSession session = new WarmupSession(
                traveler.getUniqueId(), destination.getUniqueId(), traveler.getLocation().clone(), warmupSeconds * 20);
        sessions.put(traveler.getUniqueId(), session);
        destination.sendActionBar(plugin.messages().component(
                "warmup-started-other", Map.of("player", traveler.getName()), false));

        BukkitTask task = new BukkitRunnable() {
            /** 每 5 tick 验证会话并生成粒子，每秒显示倒计时，结束后执行传送。 */
            @Override
            public void run() {
                if (sessions.get(session.travelerId) != session) {
                    cancel();
                    return;
                }
                Player currentTraveler = Bukkit.getPlayer(session.travelerId);
                Player currentDestination = Bukkit.getPlayer(session.destinationId);
                if (currentTraveler == null || currentDestination == null) {
                    cancelSession(session, "teleport-cancelled-quit", true);
                    return;
                }
                if (session.ticksRemaining <= 0) {
                    finish(session, currentTraveler, currentDestination);
                    return;
                }
                plugin.particles().spawnWarmup(currentTraveler);
                if (session.ticksRemaining % 20 == 0) {
                    currentTraveler.sendActionBar(plugin.messages().component(
                            "warmup", Map.of("seconds", Integer.toString(session.ticksRemaining / 20)), false));
                    plugin.sounds().play(currentTraveler, "countdown");
                }
                session.ticksRemaining -= 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
        session.task = task;
        return true;
    }

    /** 在玩家实际移动位置后中断其吟唱，单纯转动视角不会中断。 */
    public void handleMove(Player player, Location destination) {
        WarmupSession session = sessions.get(player.getUniqueId());
        if (session == null || destination == null) {
            return;
        }
        Location origin = session.origin;
        if (origin.getWorld() != destination.getWorld()
                || origin.getX() != destination.getX()
                || origin.getY() != destination.getY()
                || origin.getZ() != destination.getZ()) {
            cancelSession(session, "teleport-cancelled-move", true);
        }
    }

    /** 在玩家受到未取消的伤害时中断其吟唱。 */
    public void handleDamage(Player player) {
        WarmupSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            cancelSession(session, "teleport-cancelled-damage", true);
        }
    }

    /** 取消玩家作为旅行者或目的地参与的全部吟唱。 */
    public void cancelForPlayer(UUID playerId) {
        List<WarmupSession> affected = new ArrayList<>(sessions.values()).stream()
                .filter(session -> session.travelerId.equals(playerId) || session.destinationId.equals(playerId))
                .toList();
        affected.forEach(session -> cancelSession(session, "teleport-cancelled-quit", true));
    }

    /** 在插件关闭时无提示地结束所有吟唱任务。 */
    public void shutdown() {
        new ArrayList<>(sessions.values()).forEach(session -> cancelSession(session, null, false));
    }

    /** 执行最终传送，并在异步结果返回主线程后通知双方。 */
    private void finish(WarmupSession session, Player traveler, Player destination) {
        sessions.remove(session.travelerId, session);
        session.task.cancel();
        if (!canUse(traveler, destination, false)) {
            plugin.messages().sendActionBar(traveler, "teleport-cancelled-world");
            return;
        }
        Location target = destination.getLocation().clone();
        traveler.teleportAsync(target).whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !Boolean.TRUE.equals(success)) {
                plugin.messages().send(traveler, "teleport-failed");
                return;
            }
            plugin.sounds().playAt(target, "teleport");
            traveler.sendActionBar(plugin.messages().component("teleport-success", Map.of(), false));
            if (destination.isOnline()) {
                destination.sendActionBar(plugin.messages().component(
                        "teleport-success-other", Map.of("player", traveler.getName()), false));
            }
        }));
    }

    /** 移除会话、停止计时并按需通知相关在线玩家。 */
    private void cancelSession(WarmupSession session, String messageKey, boolean notify) {
        if (!sessions.remove(session.travelerId, session)) {
            return;
        }
        if (session.task != null) {
            session.task.cancel();
        }
        if (!notify || messageKey == null) {
            return;
        }
        Player traveler = Bukkit.getPlayer(session.travelerId);
        Player destination = Bukkit.getPlayer(session.destinationId);
        if (traveler != null) {
            plugin.messages().sendActionBar(traveler, messageKey);
        }
        if (destination != null) {
            plugin.messages().sendActionBar(destination, "teleport-cancelled-other");
        }
    }

    /** 检查世界是否出现在禁用列表中。 */
    private boolean isDisabledWorld(String worldName) {
        return plugin.getConfig().getStringList("disabled-worlds").contains(worldName);
    }

    /** 保存一次进行中的吟唱及其起点和倒计时任务。 */
    private static final class WarmupSession {
        private final UUID travelerId;
        private final UUID destinationId;
        private final Location origin;
        private int ticksRemaining;
        private BukkitTask task;

        /** 创建尚未绑定调度任务的吟唱状态。 */
        private WarmupSession(UUID travelerId, UUID destinationId, Location origin, int ticksRemaining) {
            this.travelerId = travelerId;
            this.destinationId = destinationId;
            this.origin = origin;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
