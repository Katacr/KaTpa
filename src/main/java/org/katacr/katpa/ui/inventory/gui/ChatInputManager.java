package org.katacr.katpa.ui.inventory.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 聊天输入捕获器，用于菜单中"添加成员/新建地标"等需要玩家输入文本的场景。
 *
 * 调用 {@link #capture} 后关闭菜单并发送提示；玩家下一次聊天消息会作为输入值回调，
 * 输入 {@code cancel} 可取消。超时（60 秒）或离线自动清理。
 */
public final class ChatInputManager implements Listener {
    private static final long TIMEOUT_MILLIS = 60_000L;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;

    /** 创建并注册聊天输入监听器。 */
    public ChatInputManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 开始捕获玩家下一次聊天输入。
     *
     * @param player   目标玩家
     * @param prompt   发送给玩家的提示文本（可为 null）
     * @param callback 输入回调，接收玩家与输入文本；取消或超时不回调
     */
    public void capture(Player player, String prompt, BiConsumer<Player, String> callback) {
        pending.put(player.getUniqueId(), new PendingInput(callback, System.currentTimeMillis() + TIMEOUT_MILLIS));
        if (prompt != null && !prompt.isBlank()) {
            player.sendMessage(prompt);
        }
    }

    /** 拦截待输入玩家的聊天事件并回调。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput input = pending.remove(event.getPlayer().getUniqueId());
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        if ("cancel".equalsIgnoreCase(message)) {
            event.getPlayer().sendMessage(text("chat-input.cancelled"));
            return;
        }
        if (System.currentTimeMillis() > input.expiresAt()) {
            event.getPlayer().sendMessage(text("chat-input.timeout"));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> input.callback().accept(event.getPlayer(), message));
    }

    /** 通过插件语言服务读取文本。 */
    private String text(String key) {
        return ((org.katacr.katpa.KaTpaPlugin) plugin).messages().text(key);
    }

    /** 玩家离线时清理待输入状态。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private record PendingInput(BiConsumer<Player, String> callback, long expiresAt) {
    }
}
