package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.model.TeleportRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 管理请求创建、交互分发、同意拒绝、超时及双击潜行状态。 */
public final class RequestService {
    private final KaTpaPlugin plugin;
    private final Map<UUID, TeleportRequest> byId = new HashMap<>();
    private final Map<UUID, TeleportRequest> outgoing = new HashMap<>();
    private final Map<UUID, Map<UUID, TeleportRequest>> incoming = new HashMap<>();
    private final Map<UUID, SneakState> sneakStates = new HashMap<>();
    private final CooldownTracker cooldowns = new CooldownTracker();

    /** 创建绑定插件服务的数据请求管理器。 */
    public RequestService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 校验双方状态并创建一条新的传送请求或邀请。 */
    public void create(Player sender, Player receiver, RequestType type) {
        if (sender.getUniqueId().equals(receiver.getUniqueId())) {
            plugin.messages().send(sender, "cannot-self");
            return;
        }
        if (outgoing.containsKey(sender.getUniqueId())) {
            plugin.messages().send(sender, "sender-busy");
            return;
        }
        long cooldownRemaining = cooldownRemaining(sender);
        if (cooldownRemaining > 0L) {
            plugin.messages().send(sender, "cooldown-active",
                    Map.of("seconds", Long.toString(cooldownRemaining)));
            return;
        }

        UUID travelerId = type == RequestType.TPA ? sender.getUniqueId() : receiver.getUniqueId();
        Player traveler = type == RequestType.TPA ? sender : receiver;
        Player destination = type == RequestType.TPA ? receiver : sender;
        if (plugin.teleports().isBusy(travelerId)) {
            plugin.messages().send(sender, "teleport-busy");
            return;
        }
        if (!plugin.teleports().canUse(traveler, destination, true)) {
            return;
        }
        startCooldown(sender);
        if (plugin.settings().hasRelation(receiver.getUniqueId(), sender.getUniqueId(), ListType.BLACKLIST)) {
            plugin.messages().send(sender, "blacklisted-sender", Map.of("player", receiver.getName()));
            plugin.messages().send(receiver, "blacklisted-receiver", Map.of("player", sender.getName()));
            return;
        }
        plugin.sounds().play(receiver, "request-received");

        UUID requestId = UUID.randomUUID();
        if (plugin.settings().hasRelation(receiver.getUniqueId(), sender.getUniqueId(), ListType.WHITELIST)) {
            long createdAt = System.currentTimeMillis();
            TeleportRequest automatic = new TeleportRequest(
                    requestId, sender.getUniqueId(), receiver.getUniqueId(), type, createdAt, createdAt, null);
            plugin.messages().send(receiver, "whitelist-auto", Map.of("player", sender.getName()));
            sendRequestCreated(sender, receiver, type);
            startAccepted(automatic, sender, receiver, false);
            return;
        }

        int timeoutSeconds = Math.max(1, plugin.getConfig().getInt("request-timeout-seconds", 30));
        long createdAt = System.currentTimeMillis();
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(
                plugin, () -> expire(requestId), timeoutSeconds * 20L);
        TeleportRequest request = new TeleportRequest(
                requestId, sender.getUniqueId(), receiver.getUniqueId(), type,
                createdAt, createdAt + timeoutSeconds * 1000L, timeoutTask);
        byId.put(requestId, request);
        outgoing.put(sender.getUniqueId(), request);
        incoming.computeIfAbsent(receiver.getUniqueId(), ignored -> new LinkedHashMap<>())
                .put(request.id(), request);
        sendRequestCreated(sender, receiver, type);

        AcceptMode mode = plugin.settings().mode(receiver.getUniqueId());
        int incomingCount = incoming(receiver.getUniqueId()).size();
        if (mode == AcceptMode.DIALOG || mode == AcceptMode.SNEAK && incomingCount > 1) {
            sneakStates.remove(receiver.getUniqueId());
            plugin.interactions().showRequestList(receiver);
        } else if (mode == AcceptMode.SNEAK) {
            sneakStates.put(receiver.getUniqueId(), new SneakState(requestId, 0L));
            plugin.interactions().presentRequest(receiver, sender, request, mode);
            plugin.interactions().refreshRequestList(receiver);
        } else {
            plugin.interactions().presentRequest(receiver, sender, request, mode);
            plugin.interactions().refreshRequestList(receiver);
        }
    }

    /** 接受指定上下文请求；无 ID 且有多条请求时打开请求列表。 */
    public void accept(Player receiver, UUID requestId) {
        List<TeleportRequest> requests = incoming(receiver.getUniqueId());
        if (requests.isEmpty()) {
            plugin.messages().send(receiver, "request-missing");
            return;
        }
        if (requestId == null && requests.size() > 1) {
            plugin.interactions().showRequestList(receiver);
            return;
        }
        TeleportRequest request = requestId == null ? requests.getFirst() : byId.get(requestId);
        if (request == null || !request.receiverId().equals(receiver.getUniqueId())) {
            plugin.messages().send(receiver, "request-stale");
            return;
        }
        Player sender = Bukkit.getPlayer(request.senderId());
        if (sender == null) {
            remove(request);
            plugin.messages().sendActionBar(receiver, "teleport-cancelled-quit");
            plugin.interactions().refreshRequestList(receiver);
            return;
        }
        if (startAccepted(request, sender, receiver, true)) {
            remove(request);
            plugin.interactions().refreshRequestList(receiver);
        } else {
            plugin.interactions().refreshRequestList(receiver);
        }
    }

    /** 拒绝指定上下文请求；无 ID 且有多条请求时打开请求列表。 */
    public void deny(Player receiver, UUID requestId) {
        List<TeleportRequest> requests = incoming(receiver.getUniqueId());
        if (requests.isEmpty()) {
            plugin.messages().send(receiver, "request-missing");
            return;
        }
        if (requestId == null && requests.size() > 1) {
            plugin.interactions().showRequestList(receiver);
            return;
        }
        TeleportRequest request = requestId == null ? requests.getFirst() : byId.get(requestId);
        if (request == null || !request.receiverId().equals(receiver.getUniqueId())) {
            plugin.messages().send(receiver, "request-stale");
            return;
        }
        Player sender = Bukkit.getPlayer(request.senderId());
        remove(request);
        plugin.messages().send(receiver, "request-denied-receiver",
                Map.of("player", sender == null ? plugin.messages().text("unknown-player") : sender.getName()));
        if (sender != null) {
            plugin.messages().send(sender, "request-denied-sender", Map.of("player", receiver.getName()));
        }
        plugin.interactions().refreshRequestList(receiver);
    }

    /** 处理潜行开始事件，并在时间窗口内第二次按下时接受请求。 */
    public void handleSneak(Player player) {
        List<TeleportRequest> requests = incoming(player.getUniqueId());
        if (requests.size() > 1) {
            sneakStates.remove(player.getUniqueId());
            plugin.interactions().showRequestList(player);
            return;
        }
        SneakState state = sneakStates.get(player.getUniqueId());
        TeleportRequest request = requests.isEmpty() ? null : requests.getFirst();
        if (state == null || request == null || !state.requestId.equals(request.id())) {
            return;
        }
        long now = System.currentTimeMillis();
        long interval = Math.max(1, plugin.getConfig().getInt("double-sneak-interval-seconds", 2)) * 1000L;
        if (state.lastPressAt > 0L && now - state.lastPressAt <= interval) {
            accept(player, request.id());
            return;
        }
        state.lastPressAt = now;
        plugin.messages().send(player, "sneak-progress");
    }

    /** 返回接收者按创建顺序排列的待处理请求快照。 */
    public List<TeleportRequest> incoming(UUID receiverId) {
        return List.copyOf(incoming.getOrDefault(receiverId, Map.of()).values());
    }

    /** 返回请求距离过期的向上取整秒数。 */
    public long remainingSeconds(TeleportRequest request) {
        long remainingMillis = request.expiresAt() - System.currentTimeMillis();
        return Math.max(0L, (remainingMillis + 999L) / 1000L);
    }

    /** 玩家离线时取消其发起和接收的全部请求并通知其他玩家。 */
    public void cancelForPlayer(UUID playerId) {
        Set<TeleportRequest> affected = new LinkedHashSet<>(incoming(playerId));
        TeleportRequest sentRequest = outgoing.get(playerId);
        if (sentRequest != null) {
            affected.add(sentRequest);
        }
        for (TeleportRequest request : affected) {
            UUID otherId = request.senderId().equals(playerId) ? request.receiverId() : request.senderId();
            Player other = Bukkit.getPlayer(otherId);
            remove(request);
            if (other != null) {
                plugin.messages().sendActionBar(other, "teleport-cancelled-quit");
                if (other.getUniqueId().equals(request.receiverId())) {
                    plugin.interactions().refreshRequestList(other);
                }
            }
        }
        plugin.interactions().stopRequestList(playerId);
    }

    /** 在插件关闭时取消全部请求及其超时任务。 */
    public void shutdown() {
        new ArrayList<>(byId.values()).forEach(this::remove);
    }

    /** 向双方发送请求创建成功消息。 */
    private void sendRequestCreated(Player sender, Player receiver, RequestType type) {
        String senderKey = type == RequestType.TPA ? "request-sent-tpa" : "request-sent-here";
        plugin.messages().send(sender, senderKey, Map.of("player", receiver.getName()));
    }

    /** 启动已同意请求的吟唱，并允许白名单流程省略接收方重复提示。 */
    private boolean startAccepted(TeleportRequest request, Player sender, Player receiver,
                                  boolean notifyReceiverAcceptance) {
        if (!plugin.teleports().begin(request)) {
            return false;
        }
        plugin.messages().send(sender, "request-accepted-sender", Map.of("player", receiver.getName()));
        if (notifyReceiverAcceptance) {
            plugin.messages().send(receiver, "request-accepted-receiver", Map.of("player", sender.getName()));
        }
        return true;
    }

    /** 将已到期请求从索引中移除并通知仍在线的双方。 */
    private void expire(UUID requestId) {
        TeleportRequest request = byId.get(requestId);
        if (request == null) {
            return;
        }
        Player sender = Bukkit.getPlayer(request.senderId());
        Player receiver = Bukkit.getPlayer(request.receiverId());
        remove(request);
        if (sender != null) {
            plugin.messages().send(sender, "request-expired-sender",
                    Map.of("player", receiver == null
                            ? plugin.messages().text("unknown-player") : receiver.getName()));
        }
        if (receiver != null) {
            plugin.messages().send(receiver, "request-expired-receiver",
                    Map.of("player", sender == null
                            ? plugin.messages().text("unknown-player") : sender.getName()));
            plugin.interactions().refreshRequestList(receiver);
        }
    }

    /** 从全部索引删除请求并停止对应超时任务。 */
    private void remove(TeleportRequest request) {
        byId.remove(request.id(), request);
        outgoing.remove(request.senderId(), request);
        Map<UUID, TeleportRequest> receiverRequests = incoming.get(request.receiverId());
        if (receiverRequests != null) {
            receiverRequests.remove(request.id(), request);
            if (receiverRequests.isEmpty()) {
                incoming.remove(request.receiverId());
            }
        }
        List<TeleportRequest> remaining = incoming(request.receiverId());
        if (remaining.size() == 1 && plugin.settings().mode(request.receiverId()) == AcceptMode.SNEAK) {
            sneakStates.put(request.receiverId(), new SneakState(remaining.getFirst().id(), 0L));
        } else {
            sneakStates.remove(request.receiverId());
        }
        if (request.timeoutTask() != null) {
            request.timeoutTask().cancel();
        }
    }

    /** 返回玩家距离可再次请求的向上取整秒数，无冷却时返回零。 */
    private long cooldownRemaining(Player player) {
        if (!plugin.getConfig().getBoolean("cooldown.enabled", true)
                || player.hasPermission("katpa.cooldown.bypass")) {
            return 0L;
        }
        long cooldownMillis = Math.max(0, plugin.getConfig().getLong("cooldown.seconds", 30L)) * 1000L;
        return cooldowns.remainingSeconds(player.getUniqueId(), System.currentTimeMillis(), cooldownMillis);
    }

    /** 在配置启用且玩家不能绕过时记录本次有效请求的冷却起点。 */
    private void startCooldown(Player player) {
        if (plugin.getConfig().getBoolean("cooldown.enabled", true)
                && !player.hasPermission("katpa.cooldown.bypass")) {
            cooldowns.start(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** 保存双击潜行请求 ID 与上一次按下时间。 */
    private static final class SneakState {
        private final UUID requestId;
        private long lastPressAt;

        /** 创建某条请求的潜行按键跟踪状态。 */
        private SneakState(UUID requestId, long lastPressAt) {
            this.requestId = requestId;
            this.lastPressAt = lastPressAt;
        }
    }
}
