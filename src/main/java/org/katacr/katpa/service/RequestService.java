package org.katacr.katpa.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.NetworkPlayer;
import org.katacr.katpa.model.NetworkRequestData;
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
    private final Map<UUID, NetworkRequestData> networkRequests = new HashMap<>();
    private final Set<UUID> networkConfirmed = new LinkedHashSet<>();
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
        plugin.sounds().play(receiver, "request-received", "tpa");

        UUID requestId = UUID.randomUUID();
        if (plugin.settings().hasRelation(receiver.getUniqueId(), sender.getUniqueId(), ListType.WHITELIST)) {
            long createdAt = System.currentTimeMillis();
            TeleportRequest automatic = new TeleportRequest(
                    requestId, sender.getUniqueId(), receiver.getUniqueId(), type, createdAt, createdAt, null);
            plugin.messages().send(receiver, "whitelist-auto", Map.of("player", sender.getName()));
            sendRequestCreated(sender, receiver.getName(), type, null);
            startAccepted(automatic, sender, receiver, false);
            return;
        }

        int timeoutSeconds = Math.max(1, plugin.getConfig().getInt("modules.tpa.request-timeout-seconds", 30));
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
        sendRequestCreated(sender, receiver.getName(), type, request);

        AcceptMode mode = plugin.settings().mode(receiver.getUniqueId());
        int incomingCount = incoming(receiver.getUniqueId()).size();
        if (mode == AcceptMode.DIALOG || mode == AcceptMode.SNEAK && incomingCount > 1) {
            sneakStates.remove(receiver.getUniqueId());
            plugin.interactions().showRequestList(receiver);
        } else if (mode == AcceptMode.SNEAK) {
            sneakStates.put(receiver.getUniqueId(), new SneakState(requestId, 0L));
            plugin.interactions().presentRequest(receiver, sender.getName(), request, mode);
            plugin.interactions().refreshRequestList(receiver);
        } else {
            plugin.interactions().presentRequest(receiver, sender.getName(), request, mode);
            plugin.interactions().refreshRequestList(receiver);
        }
    }

    /** 根据全服在线快照选择本服直连请求或 KaProxy 跨服请求。 */
    public void create(Player sender, NetworkPlayer receiver, RequestType type) {
        Player localReceiver = Bukkit.getPlayer(receiver.id());
        if (localReceiver != null) {
            create(sender, localReceiver, type);
            return;
        }
        createNetwork(sender, receiver, type);
    }

    /** 校验本地发送者状态并向 KaProxy 创建跨服传送事务。 */
    private void createNetwork(Player sender, NetworkPlayer receiver, RequestType type) {
        if (!plugin.network().available()) {
            plugin.messages().send(sender, "proxy-unavailable");
            return;
        }
        if (sender.getUniqueId().equals(receiver.id())) {
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
        if (type == RequestType.TPA && plugin.teleports().isBusy(sender.getUniqueId())) {
            plugin.messages().send(sender, "teleport-busy");
            return;
        }
        if (!plugin.teleports().canUseNetworkEndpoint(sender, true)) {
            return;
        }
        int timeoutSeconds = Math.max(1, plugin.getConfig().getInt("modules.tpa.request-timeout-seconds", 30));
        int cooldownSeconds = plugin.getConfig().getBoolean("modules.tpa.cooldown.enabled", true)
                && !sender.hasPermission("katpa.cooldown.bypass")
                ? Math.max(0, plugin.getConfig().getInt("modules.tpa.cooldown.seconds", 30)) : 0;
        long createdAt = System.currentTimeMillis();
        TeleportRequest request = new TeleportRequest(UUID.randomUUID(), sender.getUniqueId(), receiver.id(), type,
                createdAt, createdAt + timeoutSeconds * 1000L, null);
        NetworkRequestData data = new NetworkRequestData(request.id(), sender.getUniqueId(), sender.getName(),
                "local", receiver.id(), receiver.name(), receiver.server(), type,
                request.createdAt(), request.expiresAt());
        byId.put(request.id(), request);
        outgoing.put(sender.getUniqueId(), request);
        networkRequests.put(request.id(), data);
        if (!plugin.network().createRequest(sender, data, timeoutSeconds, cooldownSeconds)) {
            remove(request);
            plugin.messages().send(sender, "proxy-unavailable");
            return;
        }
        scheduleNetworkFallback(request.id(), timeoutSeconds * 1000L + 2_000L);
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
        if (networkRequests.containsKey(request.id())) {
            if (plugin.network().accept(receiver, request.id(), false)) {
                remove(request);
                plugin.interactions().refreshRequestList(receiver);
            } else {
                plugin.messages().send(receiver, "proxy-unavailable");
            }
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
        if (networkRequests.containsKey(request.id())) {
            if (plugin.network().deny(receiver, request.id(), false)) {
                remove(request);
                plugin.interactions().refreshRequestList(receiver);
            } else {
                plugin.messages().send(receiver, "proxy-unavailable");
            }
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

    /** 撤销发送者当前的请求，并用请求 ID 防止旧点击文本误撤销后续请求。 */
    public void cancel(Player sender, UUID requestId) {
        TeleportRequest request = outgoing.get(sender.getUniqueId());
        if (request == null) {
            plugin.messages().send(sender, "request-cancel-missing");
            return;
        }
        if (requestId != null && !request.id().equals(requestId)) {
            plugin.messages().send(sender, "request-stale");
            return;
        }
        if (networkRequests.containsKey(request.id())) {
            if (plugin.network().cancel(sender, request.id())) {
                remove(request);
            } else {
                plugin.messages().send(sender, "proxy-unavailable");
            }
            return;
        }
        Player receiver = Bukkit.getPlayer(request.receiverId());
        remove(request);
        plugin.messages().send(sender, "request-cancelled-sender", Map.of(
                "player", receiver == null ? plugin.messages().text("unknown-player") : receiver.getName()));
        if (receiver != null) {
            plugin.messages().send(receiver, "request-cancelled-receiver", Map.of("player", sender.getName()));
            plugin.interactions().refreshRequestList(receiver);
        }
    }

    /** 接收代理转发的跨服请求，并应用接收者本服名单和交互偏好。 */
    public void receiveNetwork(NetworkRequestData data) {
        Player receiver = Bukkit.getPlayer(data.receiverId());
        if (receiver == null || byId.containsKey(data.id())) {
            return;
        }
        TeleportRequest request = new TeleportRequest(data.id(), data.senderId(), data.receiverId(), data.type(),
                data.createdAt(), data.expiresAt(), null);
        byId.put(request.id(), request);
        networkRequests.put(request.id(), data);
        incoming.computeIfAbsent(receiver.getUniqueId(), ignored -> new LinkedHashMap<>())
                .put(request.id(), request);
        scheduleNetworkFallback(request.id(), Math.max(1_000L,
                data.expiresAt() - System.currentTimeMillis() + 2_000L));

        if (plugin.settings().hasRelation(receiver.getUniqueId(), data.senderId(), ListType.BLACKLIST)) {
            plugin.messages().send(receiver, "blacklisted-receiver", Map.of("player", data.senderName()));
            if (plugin.network().deny(receiver, request.id(), true)) {
                remove(request);
            }
            return;
        }
        plugin.sounds().play(receiver, "request-received", "tpa");
        if (plugin.settings().hasRelation(receiver.getUniqueId(), data.senderId(), ListType.WHITELIST)) {
            if (plugin.network().accept(receiver, request.id(), true)) {
                remove(request);
            }
            return;
        }
        presentPending(receiver, data.senderName(), request);
    }

    /** 接收代理创建确认，并用权威上下文替换发送端临时数据。 */
    public void networkCreated(NetworkRequestData data) {
        TeleportRequest request = byId.get(data.id());
        Player sender = Bukkit.getPlayer(data.senderId());
        if (request == null || sender == null || !networkConfirmed.add(data.id())) {
            return;
        }
        networkRequests.put(data.id(), data);
        startCooldown(sender);
        sendRequestCreated(sender, data.receiverName(), data.type(), request);
    }

    /** 处理代理的接受结果，通知双方并在旅行者源服启动吟唱。 */
    public void networkAccepted(NetworkRequestData data, boolean automatic) {
        removeNetwork(data.id());
        Player sender = Bukkit.getPlayer(data.senderId());
        Player receiver = Bukkit.getPlayer(data.receiverId());
        if (sender != null) {
            plugin.messages().send(sender, "request-accepted-sender", Map.of("player", data.receiverName()));
        }
        if (receiver != null && !automatic) {
            plugin.messages().send(receiver, "request-accepted-receiver", Map.of("player", data.senderName()));
        }
        Player traveler = Bukkit.getPlayer(data.travelerId());
        Player destination = Bukkit.getPlayer(data.destinationId());
        if (destination != null) {
            plugin.messages().sendActionBar(destination, plugin.messages().component(
                    "warmup-started-other", Map.of("player",
                            data.type() == RequestType.TPA ? data.senderName() : data.receiverName()), false));
        }
        if (traveler != null && !plugin.teleports().beginNetwork(data, traveler)) {
            plugin.network().warmupCancelled(traveler, data.id(), "teleport-cancelled-world");
        }
    }

    /** 处理代理拒绝结果，区分手动拒绝和黑名单自动拒绝。 */
    public void networkDenied(NetworkRequestData data, boolean automatic) {
        removeNetwork(data.id());
        Player sender = Bukkit.getPlayer(data.senderId());
        Player receiver = Bukkit.getPlayer(data.receiverId());
        if (sender != null) {
            plugin.messages().send(sender, automatic ? "blacklisted-sender" : "request-denied-sender",
                    Map.of("player", data.receiverName()));
        }
        if (receiver != null && !automatic) {
            plugin.messages().send(receiver, "request-denied-receiver", Map.of("player", data.senderName()));
        }
    }

    /** 处理撤销或吟唱中断结果，并刷新仍打开的请求列表。 */
    public void networkCancelled(NetworkRequestData data, String reason) {
        boolean pending = byId.containsKey(data.id());
        removeNetwork(data.id());
        Player sender = Bukkit.getPlayer(data.senderId());
        Player receiver = Bukkit.getPlayer(data.receiverId());
        if ("sender-cancelled".equals(reason)) {
            if (sender != null) {
                plugin.messages().send(sender, "request-cancelled-sender", Map.of("player", data.receiverName()));
            }
            if (receiver != null) {
                plugin.messages().send(receiver, "request-cancelled-receiver", Map.of("player", data.senderName()));
            }
            return;
        }
        if (pending && "player-offline".equals(reason)) {
            if (sender != null) {
                plugin.messages().send(sender, "request-expired-sender", Map.of("player", data.receiverName()));
            }
            if (receiver != null) {
                plugin.messages().send(receiver, "request-expired-receiver", Map.of("player", data.senderName()));
            }
            return;
        }
        Player traveler = Bukkit.getPlayer(data.travelerId());
        Player destination = Bukkit.getPlayer(data.destinationId());
        String messageKey = networkCancellationMessage(reason);
        if (traveler != null) {
            plugin.messages().sendActionBar(traveler, messageKey);
        }
        if (destination != null) {
            plugin.messages().sendActionBar(destination, "teleport-cancelled-other");
        }
    }

    /** 处理代理权威请求超时。 */
    public void networkExpired(NetworkRequestData data) {
        removeNetwork(data.id());
        Player sender = Bukkit.getPlayer(data.senderId());
        Player receiver = Bukkit.getPlayer(data.receiverId());
        if (sender != null) {
            plugin.messages().send(sender, "request-expired-sender", Map.of("player", data.receiverName()));
        }
        if (receiver != null) {
            plugin.messages().send(receiver, "request-expired-receiver", Map.of("player", data.senderName()));
        }
    }

    /** 处理接受后的跨服事务失败。 */
    public void networkFailed(NetworkRequestData data, String reason) {
        removeNetwork(data.id());
        for (UUID playerId : List.of(data.senderId(), data.receiverId())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.messages().sendActionBar(player, "network-transaction-failed",
                        Map.of("reason", plugin.messages().text("network-reason." + reason)));
            }
        }
    }

    /** 在旅行者进入目标服后读取目的地玩家最新位置并完成传送。 */
    public void networkArrival(NetworkRequestData data) {
        attemptNetworkArrival(data, 0);
    }

    /** 等待旅行者完成 Bukkit 登录阶段，再消费代理到达凭证。 */
    private void attemptNetworkArrival(NetworkRequestData data, int attempt) {
        Player traveler = Bukkit.getPlayer(data.travelerId());
        Player destination = Bukkit.getPlayer(data.destinationId());
        if (traveler == null && attempt < 20) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> attemptNetworkArrival(data, attempt + 1), 1L);
            return;
        }
        if (traveler == null) {
            return;
        }
        if (destination == null) {
            plugin.network().arrivalFailed(traveler, data.id(), "player-offline");
            return;
        }
        if (!traveler.isOnline()) {
            if (attempt < 20) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> attemptNetworkArrival(data, attempt + 1), 1L);
            }
            return;
        }
        plugin.teleports().arriveNetwork(data.id(), traveler, destination);
    }

    /** 接收最终完成确认；实际成功反馈已由目标服落点流程发送。 */
    public void networkCompleted(NetworkRequestData data) {
        removeNetwork(data.id());
    }

    /** 处理代理对创建或旧上下文操作的即时拒绝。 */
    public void networkOperationFailed(Player actor, UUID requestId, String reason, long seconds) {
        NetworkRequestData data = networkRequests.get(requestId);
        TeleportRequest request = byId.get(requestId);
        if (request != null) {
            remove(request);
        }
        switch (reason) {
            case "cooldown-active" -> plugin.messages().send(actor, "cooldown-active",
                    Map.of("seconds", Long.toString(seconds)));
            case "sender-busy" -> plugin.messages().send(actor, "sender-busy");
            case "cannot-self" -> plugin.messages().send(actor, "cannot-self");
            case "player-not-found", "target-unavailable" -> plugin.messages().send(actor,
                    "player-not-found", Map.of("player", data == null
                            ? plugin.messages().text("unknown-player") : data.receiverName()));
            case "same-server" -> plugin.messages().send(actor, "network-target-moved");
            case "request-stale" -> plugin.messages().send(actor, "request-stale");
            default -> plugin.messages().send(actor, "network-request-failed");
        }
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
        long interval = Math.max(1, plugin.getConfig().getInt("modules.tpa.double-sneak-interval-seconds", 2)) * 1000L;
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

    /** 返回本地或跨服请求创建时保存的发送者名称。 */
    public String senderName(TeleportRequest request) {
        NetworkRequestData data = networkRequests.get(request.id());
        if (data != null) {
            return data.senderName();
        }
        Player sender = Bukkit.getPlayer(request.senderId());
        return sender == null ? plugin.messages().text("unknown-player") : sender.getName();
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

    /** 向发送者发送请求成功消息；待处理请求额外附加撤销入口。 */
    private void sendRequestCreated(Player sender, String receiverName, RequestType type, TeleportRequest request) {
        String senderKey = type == RequestType.TPA ? "request-sent-tpa" : "request-sent-here";
        if (request == null) {
            plugin.messages().send(sender, senderKey, Map.of("player", receiverName));
            return;
        }
        plugin.interactions().sendCancellableRequestCreated(sender, receiverName, request, senderKey);
    }

    /** 根据接受模式展示本地或跨服待处理请求。 */
    private void presentPending(Player receiver, String senderName, TeleportRequest request) {
        AcceptMode mode = plugin.settings().mode(receiver.getUniqueId());
        int incomingCount = incoming(receiver.getUniqueId()).size();
        if (mode == AcceptMode.DIALOG || mode == AcceptMode.SNEAK && incomingCount > 1) {
            sneakStates.remove(receiver.getUniqueId());
            plugin.interactions().showRequestList(receiver);
        } else if (mode == AcceptMode.SNEAK) {
            sneakStates.put(receiver.getUniqueId(), new SneakState(request.id(), 0L));
            plugin.interactions().presentRequest(receiver, senderName, request, mode);
            plugin.interactions().refreshRequestList(receiver);
        } else {
            plugin.interactions().presentRequest(receiver, senderName, request, mode);
            plugin.interactions().refreshRequestList(receiver);
        }
    }

    /** 删除跨服请求的本地镜像并刷新接收方界面。 */
    private void removeNetwork(UUID requestId) {
        TeleportRequest request = byId.get(requestId);
        if (request == null) {
            networkRequests.remove(requestId);
            return;
        }
        Player receiver = Bukkit.getPlayer(request.receiverId());
        remove(request);
        if (receiver != null) {
            plugin.interactions().refreshRequestList(receiver);
        }
    }

    /** 把代理原因代码限制到现有 ActionBar 语言节点。 */
    private String networkCancellationMessage(String reason) {
        return switch (reason) {
            case "teleport-cancelled-move", "teleport-cancelled-damage",
                    "teleport-cancelled-world", "teleport-cancelled-quit" -> reason;
            case "player-offline" -> "teleport-cancelled-quit";
            default -> "teleport-cancelled-other";
        };
    }

    /** 在代理事件丢失时本地清理镜像，防止请求永久占用玩家状态。 */
    private void scheduleNetworkFallback(UUID requestId, long delayMillis) {
        long delayTicks = Math.max(1L, (delayMillis + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            NetworkRequestData data = networkRequests.get(requestId);
            TeleportRequest request = byId.get(requestId);
            if (data == null || request == null) {
                return;
            }
            Player sender = Bukkit.getPlayer(data.senderId());
            Player receiver = Bukkit.getPlayer(data.receiverId());
            remove(request);
            if (sender != null) {
                plugin.messages().send(sender, "request-expired-sender", Map.of("player", data.receiverName()));
            }
            if (receiver != null) {
                plugin.messages().send(receiver, "request-expired-receiver", Map.of("player", data.senderName()));
                plugin.interactions().refreshRequestList(receiver);
            }
        }, delayTicks);
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
        networkRequests.remove(request.id());
        networkConfirmed.remove(request.id());
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
        if (!plugin.getConfig().getBoolean("modules.tpa.cooldown.enabled", true)
                || player.hasPermission("katpa.cooldown.bypass")) {
            return 0L;
        }
        long cooldownMillis = Math.max(0, plugin.getConfig().getLong("modules.tpa.cooldown.seconds", 30L)) * 1000L;
        return cooldowns.remainingSeconds(player.getUniqueId(), System.currentTimeMillis(), cooldownMillis);
    }

    /** 在配置启用且玩家不能绕过时记录本次有效请求的冷却起点。 */
    private void startCooldown(Player player) {
        if (plugin.getConfig().getBoolean("modules.tpa.cooldown.enabled", true)
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
