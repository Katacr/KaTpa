package org.katacr.katpa.ui.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.model.TeleportRequest;
import org.katacr.katpa.model.Home;
import org.katacr.katpa.model.Warp;
import org.katacr.katpa.ui.InteractionPlatform;
import org.katacr.katpa.ui.PlatformNamed;
import org.katacr.katpa.ui.inventory.gui.GuiManager;
import org.katacr.katpa.ui.inventory.gui.KaTpaGuiActions;
import org.katacr.katpa.ui.inventory.gui.KaTpaGuiListProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于原生容器（Chest）库存与 gui/ 资源 YAML 的 KaTpa 交互实现。
 *
 * 替代原 Paper/Spigot Dialog 适配器，向 1.16.5 与 Java 16 兼容。菜单布局、按钮与动作
 * 全部由 {@code resources/gui/*.yml} 驱动，首次开服提取到 {@code plugins/KaTpa/gui/} 供定制。
 */
public final class InventoryInteractionPlatform implements InteractionPlatform, PlatformNamed {
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private final KaTpaPlugin plugin;
    private final GuiManager gui;
    private final Map<UUID, String> requestWindow = new ConcurrentHashMap<>();

    /** 创建绑定插件的容器交互实现。 */
    public InventoryInteractionPlatform(KaTpaPlugin plugin) {
        this.plugin = plugin;
        this.gui = new GuiManager(plugin);
    }

    @Override
    public void initialize(KaTpaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new InventoryMenuListener(gui), plugin);
        org.katacr.katpa.ui.inventory.gui.ChatInputManager chat =
                new org.katacr.katpa.ui.inventory.gui.ChatInputManager(plugin);
        org.katacr.katpa.ui.inventory.gui.KaTpaGuiActions actions =
                new org.katacr.katpa.ui.inventory.gui.KaTpaGuiActions(plugin, chat, gui);
        gui.registerActionHandler("setting", actions);
        gui.registerActionHandler("relation", actions);
        gui.registerActionHandler("warp", actions);
        gui.registerActionHandler("home", actions);
        gui.registerActionHandler("request", actions);
        gui.registerActionHandler("page", actions);
        gui.registerListProvider(new KaTpaGuiListProvider(plugin));
        gui.reload();
    }

    @Override
    public String platformName() {
        return "Inventory";
    }

    /** 返回底层 GUI 管理器，供监听器路由点击。 */
    public GuiManager gui() {
        return gui;
    }

    @Override
    public void sendActionBar(Player player, Component message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(SECTION.serialize(message)));
    }

    @Override
    public void sendCancellableRequestCreated(Player sender, String receiverName, TeleportRequest request, String messageKey) {
        plugin.messages().send(sender, messageKey, java.util.Map.of("player", receiverName));
    }

    @Override
    public void presentRequest(Player receiver, String senderName, TeleportRequest request, AcceptMode mode) {
        List<TeleportRequest> pending = plugin.requests().incoming(receiver.getUniqueId());
        if (pending.size() >= 2) {
            switchToRequestList(receiver);
        } else {
            switchToHopper(receiver, request, senderName);
        }
    }

    @Override
    public void showRequestList(Player receiver) {
        switchToRequestList(receiver);
    }

    @Override
    public void refreshRequestList(Player receiver) {
        List<TeleportRequest> pending = plugin.requests().incoming(receiver.getUniqueId());
        if (pending.isEmpty()) {
            closeRequestWindow(receiver);
            return;
        }
        if (pending.size() >= 2) {
            switchToRequestList(receiver);
        } else {
            switchToHopper(receiver, pending.get(0), plugin.requests().senderName(pending.get(0)));
        }
    }

    @Override
    public void stopRequestList(UUID playerId) {
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player != null) {
            closeRequestWindow(player);
        }
    }

    /** 关闭接收者当前的请求窗口（漏斗或容器列表），清理会话状态。 */
    private void closeRequestWindow(Player player) {
        String current = requestWindow.remove(player.getUniqueId());
        if ("hopper".equals(current)) {
            InventoryMenu currentMenu = InventoryMenu.currentOf(player);
            if (currentMenu instanceof RequestHopperMenu) {
                ((RequestHopperMenu) currentMenu).close();
            } else {
                player.closeInventory();
            }
        } else if ("list".equals(current)) {
            player.closeInventory();
            gui.handleClose(player);
        } else {
            player.closeInventory();
        }
        requestWindow.remove(player.getUniqueId());
    }

    /** 切换到容器请求列表（如已有则复用其刷新）。 */
    private void switchToRequestList(Player receiver) {
        String current = requestWindow.get(receiver.getUniqueId());
        if ("list".equals(current)) {
            gui.reopen(receiver);
            return;
        }
        closeRequestWindow(receiver);
        requestWindow.put(receiver.getUniqueId(), "list");
        gui.openMenu(receiver, "request_list");
    }

    /** 切换到单条漏斗窗口。 */
    private void switchToHopper(Player receiver, TeleportRequest request, String senderName) {
        String current = requestWindow.get(receiver.getUniqueId());
        if ("hopper".equals(current)) {
            return;
        }
        closeRequestWindow(receiver);
        requestWindow.put(receiver.getUniqueId(), "hopper");
        new RequestHopperMenu(plugin, receiver, request, senderName).open();
    }

    @Override
    public void showPlayerSelector(Player player, RequestType type) {
        gui.openMenu(player, "player_selector");
    }

    @Override
    public void showSettings(Player player) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("mode_display", plugin.messages().text(plugin.settings().mode(player.getUniqueId()).languageKey()));
        vars.put("whitelist_count", String.valueOf(plugin.settings().relations(player.getUniqueId(), ListType.WHITELIST).size()));
        vars.put("blacklist_count", String.valueOf(plugin.settings().relations(player.getUniqueId(), ListType.BLACKLIST).size()));
        gui.openMenu(player, "settings", "", vars);
    }

    @Override
    public void showRelationEditor(Player player, ListType type) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("list_type", type.name().toLowerCase());
        vars.put("list_type_display", "whitelist".equalsIgnoreCase(type.name()) ? "白名单" : "黑名单");
        gui.openMenu(player, "relation_editor:" + type.name().toLowerCase(), type.name().toLowerCase(), vars);
    }

    @Override
    public void showWarpSelector(Player player) {
        gui.openMenu(player, "warp_selector");
    }

    @Override
    public void showWarpManager(Player player) {
        gui.openMenu(player, "warp_manager");
    }

    @Override
    public void showWarpEditor(Player player, Warp warp) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        if (warp != null) {
            vars.put("warp_name", warp.name());
            vars.put("warp_permission", warp.permission() == null ? "" : warp.permission());
            vars.put("warp_cooldown", String.valueOf(warp.cooldownSeconds()));
            vars.put("warp_cost", String.valueOf(warp.cost()));
            vars.put("warp_description", warp.description() == null ? "" : warp.description());
            vars.put("warp_icon", warp.iconMaterial() == null ? "" : warp.iconMaterial());
            vars.put("warp_custom_data", warp.iconCustomData() == null ? "" : String.valueOf(warp.iconCustomData()));
            vars.put("warp_item_model", warp.iconItemModel() == null ? "" : warp.iconItemModel());
        }
        gui.openMenu(player, "warp_editor", "", vars);
    }

    @Override
    public void showPwarpSelector(Player player) {
        gui.openMenu(player, "pwarp_selector");
    }

    @Override
    public void showPwarpManager(Player player) {
        gui.openMenu(player, "pwarp_manager");
    }

    @Override
    public void showPwarpEditor(Player player, org.katacr.katpa.model.PlayerWarp warp) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        if (warp != null) {
            vars.put("pwarp_name", warp.name());
            vars.put("pwarp_description", warp.description() == null ? "" : warp.description());
            vars.put("pwarp_cooldown", String.valueOf(warp.cooldownSeconds()));
            vars.put("pwarp_cost", String.valueOf(warp.cost()));
            vars.put("pwarp_icon", warp.iconMaterial() == null ? org.katacr.katpa.model.PlayerWarp.DEFAULT_ICON : warp.iconMaterial());
            vars.put("pwarp_custom_data", warp.iconCustomData() == null ? "" : String.valueOf(warp.iconCustomData()));
            vars.put("pwarp_item_model", warp.iconItemModel() == null ? "" : warp.iconItemModel());
        }
        gui.openMenu(player, "pwarp_editor", "", vars);
    }

    @Override
    public void showPwarpRate(Player player, org.katacr.katpa.model.PlayerWarp warp) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        if (warp != null) {
            vars.put("pwarp_name", warp.name());
        }
        gui.openMenu(player, "pwarp_rate", "", vars);
    }

    @Override
    public void showHomeSelector(Player player) {
        gui.openMenu(player, "home_selector");
    }

    @Override
    public void showHomeManager(Player player) {
        gui.openMenu(player, "home_manager");
    }

    @Override
    public void shutdown() {
        // 菜单会话随玩家退出/关服自动清理，无需额外操作。
    }
}
