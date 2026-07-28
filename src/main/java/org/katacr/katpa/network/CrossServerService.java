package org.katacr.katpa.network;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.NetworkPlayer;
import org.katacr.katpa.model.NetworkRequestData;
import org.katacr.katpa.model.RequestType;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 管理 KaTpa 后端的 KaProxy 通道、全服玩家快照和跨服事务消息。 */
public final class CrossServerService implements PluginMessageListener {
    private final KaTpaPlugin plugin;
    private final Map<UUID, NetworkPlayer> onlinePlayers = new LinkedHashMap<>();
    private volatile boolean available;
    private volatile long lastPresenceAt;
    private boolean registered;
    private BukkitTask heartbeatTask;

    /** 创建绑定插件服务的跨服通讯层。 */
    public CrossServerService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 按配置注册统一代理通道，并主动请求在线玩家同步。 */
    public void initialize() {
        if (!enabled()) {
            return;
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, KaProxyProtocol.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, KaProxyProtocol.CHANNEL, this);
        registered = true;
        plugin.getServer().getOnlinePlayers().stream().findFirst().ifPresent(this::requestSync);
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                        plugin.getServer().getOnlinePlayers().stream().findFirst().ifPresent(this::requestSync),
                20L * 30L, 20L * 30L);
    }

    /** 注销通道并清理代理在线快照。 */
    public void shutdown() {
        if (registered) {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
                    plugin, KaProxyProtocol.CHANNEL, this);
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, KaProxyProtocol.CHANNEL);
            registered = false;
        }
        onlinePlayers.clear();
        available = false;
        lastPresenceAt = 0L;
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    /** 返回配置是否允许接入 KaProxy。 */
    public boolean enabled() {
        return plugin.getConfig().getBoolean("proxy.enabled", false);
    }

    /** 返回是否已经收到 KaProxy 的有效在线玩家快照。 */
    public boolean available() {
        return enabled() && available && System.currentTimeMillis() - lastPresenceAt <= 90_000L;
    }

    /** 玩家进入子服后下一刻向代理请求最新全服在线列表。 */
    public void handleJoin(Player player) {
        if (enabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    requestSync(player);
                }
            }, 1L);
        }
    }

    /** 返回本服和代理快照合并后的在线玩家列表。 */
    public List<NetworkPlayer> onlinePlayers() {
        Map<UUID, NetworkPlayer> merged = new LinkedHashMap<>();
        if (available()) {
            synchronized (onlinePlayers) {
                merged.putAll(onlinePlayers);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            merged.put(player.getUniqueId(), new NetworkPlayer(player.getUniqueId(), player.getName(), "local"));
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(NetworkPlayer::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** 按名称查找本服或全服在线玩家。 */
    public NetworkPlayer findPlayerExact(String name) {
        Player local = Bukkit.getPlayerExact(name);
        if (local != null) {
            return new NetworkPlayer(local.getUniqueId(), local.getName(), "local");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        return onlinePlayers().stream()
                .filter(player -> player.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst().orElse(null);
    }

    /** 向代理创建一条跨服请求。 */
    public boolean createRequest(Player sender, NetworkRequestData request, int timeoutSeconds, int cooldownSeconds) {
        return send(sender, "tpa", "request_create", output -> {
            KaProxyProtocol.writeUuid(output, request.id());
            KaProxyProtocol.writeUuid(output, request.receiverId());
            output.writeUTF(request.type().name());
            output.writeInt(timeoutSeconds);
            output.writeInt(cooldownSeconds);
        });
    }

    /** 接受一条跨服请求，可标记为白名单自动接受。 */
    public boolean accept(Player receiver, UUID requestId, boolean automatic) {
        return send(receiver, "tpa", "request_accept", output -> {
            KaProxyProtocol.writeUuid(output, requestId);
            output.writeBoolean(automatic);
        });
    }

    /** 拒绝一条跨服请求，可标记为黑名单自动拒绝。 */
    public boolean deny(Player receiver, UUID requestId, boolean automatic) {
        return send(receiver, "tpa", "request_deny", output -> {
            KaProxyProtocol.writeUuid(output, requestId);
            output.writeBoolean(automatic);
        });
    }

    /** 撤销发送者自己的跨服请求。 */
    public boolean cancel(Player sender, UUID requestId) {
        return send(sender, "tpa", "request_cancel", output -> KaProxyProtocol.writeUuid(output, requestId));
    }

    /** 通知代理旅行者已完成源服吟唱。 */
    public boolean warmupComplete(Player traveler, UUID requestId) {
        return send(traveler, "tpa", "warmup_complete",
                output -> KaProxyProtocol.writeUuid(output, requestId));
    }

    /** 通知代理源服吟唱因指定原因中断。 */
    public boolean warmupCancelled(Player traveler, UUID requestId, String reason) {
        return send(traveler, "tpa", "warmup_cancel", output -> {
            KaProxyProtocol.writeUuid(output, requestId);
            output.writeUTF(reason);
        });
    }

    /** 通知代理目标服已完成最终传送。 */
    public boolean arrivalComplete(Player traveler, UUID requestId) {
        return send(traveler, "tpa", "arrival_complete",
                output -> KaProxyProtocol.writeUuid(output, requestId));
    }

    /** 通知代理目标服无法完成最终传送。 */
    public boolean arrivalFailed(Player traveler, UUID requestId, String reason) {
        return send(traveler, "tpa", "arrival_failed", output -> {
            KaProxyProtocol.writeUuid(output, requestId);
            output.writeUTF(reason);
        });
    }

    /** 解码代理事件并在 Bukkit 主线程内交给请求服务。 */
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier,
                                        byte @NotNull [] message) {
        if (!KaProxyProtocol.CHANNEL.equals(channel)) {
            return;
        }
        try {
            KaProxyProtocol.Packet packet = KaProxyProtocol.decode(message);
            available = true;
            lastPresenceAt = System.currentTimeMillis();
            if ("core".equals(packet.module()) && "presence".equals(packet.action())) {
                readPresence(packet.input());
                return;
            }
            if (!"tpa".equals(packet.module())) {
                return;
            }
            handleTpa(carrier, packet.action(), packet.input());
        } catch (IOException | RuntimeException error) {
            plugin.getLogger().warning("无法解析 KaProxy 数据包: " + error.getMessage());
        }
    }

    /** 更新代理提供的全服在线玩家快照。 */
    private void readPresence(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > 100_000) {
            throw new IOException("在线玩家数量无效: " + count);
        }
        Map<UUID, NetworkPlayer> snapshot = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            UUID id = KaProxyProtocol.readUuid(input);
            snapshot.put(id, new NetworkPlayer(id, input.readUTF(), input.readUTF()));
        }
        synchronized (onlinePlayers) {
            onlinePlayers.clear();
            onlinePlayers.putAll(snapshot);
        }
        available = true;
        lastPresenceAt = System.currentTimeMillis();
    }

    /** 路由 KaTpa 模块动作。 */
    private void handleTpa(Player carrier, String action, DataInputStream input) throws IOException {
        switch (action) {
            case "request_incoming" -> plugin.requests().receiveNetwork(readRequest(input));
            case "request_created" -> plugin.requests().networkCreated(readRequest(input));
            case "request_accepted" -> plugin.requests().networkAccepted(readRequest(input), input.readBoolean());
            case "request_denied" -> plugin.requests().networkDenied(readRequest(input), input.readBoolean());
            case "request_cancelled" -> plugin.requests().networkCancelled(readRequest(input), input.readUTF());
            case "request_expired" -> plugin.requests().networkExpired(readRequest(input));
            case "request_failed" -> plugin.requests().networkFailed(readRequest(input), input.readUTF());
            case "arrival" -> plugin.requests().networkArrival(readRequest(input));
            case "request_completed" -> plugin.requests().networkCompleted(readRequest(input));
            case "operation_failed" -> plugin.requests().networkOperationFailed(carrier,
                    KaProxyProtocol.readUuid(input), input.readUTF(), input.readLong());
            default -> plugin.getLogger().fine("忽略未知 KaTpa 代理动作: " + action);
        }
    }

    /** 读取代理事件携带的完整请求上下文。 */
    private NetworkRequestData readRequest(DataInputStream input) throws IOException {
        UUID id = KaProxyProtocol.readUuid(input);
        UUID senderId = KaProxyProtocol.readUuid(input);
        String senderName = input.readUTF();
        String senderServer = input.readUTF();
        UUID receiverId = KaProxyProtocol.readUuid(input);
        String receiverName = input.readUTF();
        String receiverServer = input.readUTF();
        RequestType type = RequestType.valueOf(input.readUTF());
        return new NetworkRequestData(id, senderId, senderName, senderServer,
                receiverId, receiverName, receiverServer, type, input.readLong(), input.readLong());
    }

    /** 请求 KaProxy 发送一次在线玩家快照。 */
    private void requestSync(Player carrier) {
        send(carrier, "core", "sync_request", output -> { });
    }

    /** 编码并使用指定在线玩家连接发送插件消息。 */
    private boolean send(Player carrier, String module, String action, KaProxyProtocol.PacketWriter writer) {
        if (!available() && !"sync_request".equals(action)) {
            return false;
        }
        try {
            carrier.sendPluginMessage(plugin, KaProxyProtocol.CHANNEL,
                    KaProxyProtocol.encode(module, action, writer));
            return true;
        } catch (IOException error) {
            plugin.getLogger().warning("无法编码 KaProxy 数据包: " + error.getMessage());
            return false;
        }
    }
}
