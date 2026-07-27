package org.katacr.katpa.ui.spigot;

import com.google.gson.JsonElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEventCustom;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.dialog.Dialog;
import net.md_5.bungee.api.dialog.DialogBase;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.NoticeDialog;
import net.md_5.bungee.api.dialog.action.ActionButton;
import net.md_5.bungee.api.dialog.action.CustomClickAction;
import net.md_5.bungee.api.dialog.body.DialogBody;
import net.md_5.bungee.api.dialog.body.PlainMessageBody;
import net.md_5.bungee.api.dialog.input.DialogInput;
import net.md_5.bungee.api.dialog.input.InputOption;
import net.md_5.bungee.api.dialog.input.SingleOptionInput;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCustomClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.KnownPlayer;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.RelationEntry;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.model.TeleportRequest;
import org.katacr.katpa.ui.InteractionPlatform;
import org.katacr.katpa.ui.InteractionService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 使用 Spigot 1.21.6+ 的 Bungee Dialog API 渲染 KaTpa 的交互界面。
 *
 * Spigot 无 Adventure ClickCallback / DialogAction.customClick，因此每个按钮与内联可点击文本
 * 都注册一次性 custom-click session，命中 {@link PlayerCustomClickEvent} 后校验玩家并执行
 * 服务端预存的回调。业务逻辑仍复用 RequestService 与 SettingsStore。
 */
public final class SpigotInteractionPlatform implements InteractionPlatform, InteractionService.PlatformNamed, Listener {
    private static final String ACCEPT_MODE_INPUT = "accept_mode";

    private final AtomicLong callbackSequence = new AtomicLong();
    private final Map<String, CallbackSession> callbacks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerCallbacks = new ConcurrentHashMap<>();
    private final Set<UUID> requestListViewers = ConcurrentHashMap.newKeySet();
    private KaTpaPlugin plugin;

    /** 服务端保存的可信按钮回调上下文；仅存玩家身份与固定业务动作。 */
    private record CallbackSession(UUID playerId, CallbackAction action, long expiresAtMillis) {
    }

    /** 执行已校验的 Spigot Dialog custom-click，并可读取客户端提交的输入值。 */
    @FunctionalInterface
    private interface CallbackAction {
        /** 在主线程执行回调动作，payload 来自当前 Dialog 输入。 */
        void run(Player player, JsonElement payload);
    }

    /** 返回该适配器所用平台名称。 */
    @Override
    public String platformName() {
        return "Spigot";
    }

    /** 注册 custom-click 与退出监听器。 */
    @Override
    public void initialize(KaTpaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** 按接收者偏好显示一条待处理请求。 */
    @Override
    public void presentRequest(Player receiver, Player sender, TeleportRequest request, AcceptMode mode) {
        String messageKey = request.type() == RequestType.TPA
                ? "request-received-tpa" : "request-received-here";
        switch (mode) {
            case DIALOG -> showRequestList(receiver);
            case CHAT -> showChatActions(receiver, sender, request, messageKey);
            case SNEAK -> send(receiver, plugin.messages().component(
                            messageKey, Map.of("player", sender.getName()), true)
                    .append(Component.space())
                    .append(plugin.messages().component("sneak-hint", Map.of(), false)));
        }
    }

    /** 打开待处理请求列表，并记录需要在请求变化时接收更新的玩家。 */
    @Override
    public void showRequestList(Player receiver) {
        if (plugin.requests().incoming(receiver.getUniqueId()).isEmpty()) {
            return;
        }
        requestListViewers.add(receiver.getUniqueId());
        renderRequestList(receiver);
    }

    /** 请求发生增删后，仅在列表已打开时直接推送最新 Dialog。 */
    @Override
    public void refreshRequestList(Player receiver) {
        if (!requestListViewers.contains(receiver.getUniqueId())) {
            return;
        }
        if (plugin.requests().incoming(receiver.getUniqueId()).isEmpty()) {
            stopRequestList(receiver.getUniqueId());
            clearCallbacks(receiver.getUniqueId());
            receiver.clearDialog();
            return;
        }
        renderRequestList(receiver);
    }

    /** 停止指定玩家后续接收请求列表变化推送。 */
    @Override
    public void stopRequestList(UUID playerId) {
        requestListViewers.remove(playerId);
    }

    /** 在插件关闭时清空全部回调与查看状态。 */
    @Override
    public void shutdown() {
        callbacks.clear();
        playerCallbacks.clear();
        requestListViewers.clear();
    }

    /** 验证并消费一次性按钮 session，然后执行预存的业务动作。 */
    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        CallbackSession session = removeCallback(event.getId().toString());
        if (session == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!session.playerId().equals(player.getUniqueId())
                || session.expiresAtMillis() < System.currentTimeMillis()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> session.action().run(player, event.getData()));
    }

    /** 移除断线玩家的全部未消费回调与查看状态。 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopRequestList(event.getPlayer().getUniqueId());
        clearCallbacks(event.getPlayer().getUniqueId());
    }

    /** 构建带上下文同意/拒绝文本的请求列表并直接替换当前 Dialog。 */
    private void renderRequestList(Player receiver) {
        clearCallbacks(receiver.getUniqueId());
        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> fallbackButtons = new ArrayList<>();
        body.add(new PlainMessageBody(text(plugin.messages().component(
                "ui.request-list.header", Map.of(), false)), 500));
        for (TeleportRequest request : plugin.requests().incoming(receiver.getUniqueId())) {
            Player sender = Bukkit.getPlayer(request.senderId());
            String senderName = sender == null ? plugin.messages().text("unknown-player") : sender.getName();
            String messageKey = request.type() == RequestType.TPA
                    ? "ui.request-list.tpa" : "ui.request-list.here";
            UUID requestId = request.id();
            TextComponent line = new TextComponent(text(plugin.messages().component(messageKey, Map.of(
                    "player", senderName,
                    "seconds", Long.toString(plugin.requests().remainingSeconds(request))), false)));
            line.addExtra(" ");
            line.addExtra(clickable(receiver,
                    plugin.messages().component("ui.button.accept-bracket", Map.of(), false),
                    plugin.messages().component("ui.chat.accept-hover", Map.of(), false),
                    () -> plugin.requests().accept(receiver, requestId)));
            line.addExtra(" ");
            line.addExtra(clickable(receiver,
                    plugin.messages().component("ui.button.deny-bracket", Map.of(), false),
                    plugin.messages().component("ui.chat.deny-hover", Map.of(), false),
                    () -> plugin.requests().deny(receiver, requestId)));
            body.add(new PlainMessageBody(line, 500));
            fallbackButtons.add(button(receiver, plugin.messages().component(
                            "ui.request-list.accept-button", Map.of("player", senderName), false), null,
                    () -> plugin.requests().accept(receiver, requestId)));
        }
        ActionButton close = button(receiver, plugin.messages().component(
                "ui.button.close", Map.of(), false), null, () -> stopRequestList(receiver.getUniqueId()));
        DialogBase base = new DialogBase(text(plugin.messages().component(
                "ui.request-list.title", Map.of(), false)))
                .body(body)
                .canCloseWithEscape(false)
                .pause(false)
                .afterAction(DialogBase.AfterAction.CLOSE);
        receiver.showDialog(new MultiActionDialog(base, fallbackButtons, 1, close));
    }

    /** 显示未指定玩家参数时使用的在线玩家 Dialog 选择器。 */
    @Override
    public void showPlayerSelector(Player player, RequestType type) {
        List<ActionButton> buttons = Bukkit.getOnlinePlayers().stream()
                .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
                .sorted((first, second) -> first.getName().compareToIgnoreCase(second.getName()))
                .map(target -> {
                    UUID targetId = target.getUniqueId();
                    String targetName = target.getName();
                    return button(player, Component.text(targetName),
                            plugin.messages().component(type == RequestType.TPA
                                    ? "ui.selector.tooltip-tpa" : "ui.selector.tooltip-here", Map.of(), false),
                            () -> {
                                Player currentTarget = Bukkit.getPlayer(targetId);
                                if (currentTarget == null) {
                                    plugin.messages().send(player, "player-not-found", Map.of("player", targetName));
                                    return;
                                }
                                plugin.requests().create(player, currentTarget, type);
                            });
                })
                .toList();

        Component title = plugin.messages().component(type == RequestType.TPA
                ? "ui.selector.title-tpa" : "ui.selector.title-here", Map.of(), false);
        Component bodyText = buttons.isEmpty()
                ? plugin.messages().component("ui.selector.empty", Map.of(), false)
                : plugin.messages().component("ui.selector.count",
                Map.of("count", Integer.toString(buttons.size())), false);
        showActionDialog(player, title, bodyText, buttons, 3);
    }

    /** 显示用于选择接受模式和管理名单的个人设置 Dialog。 */
    @Override
    public void showSettings(Player player) {
        clearCallbacks(player.getUniqueId());
        AcceptMode currentMode = plugin.settings().mode(player.getUniqueId());
        List<InputOption> modeOptions = new ArrayList<>();
        for (AcceptMode mode : AcceptMode.values()) {
            modeOptions.add(new InputOption(mode.name().toLowerCase(),
                    text(plugin.messages().component(mode.languageKey(), Map.of(), false)),
                    mode == currentMode));
        }
        SingleOptionInput modeInput = new SingleOptionInput(ACCEPT_MODE_INPUT, 300,
                text(plugin.messages().component("ui.settings.mode-label", Map.of(), false)), true, modeOptions);

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(player, plugin.messages().component("ui.settings.manage-whitelist", Map.of(), false),
                plugin.messages().component("ui.settings.whitelist-hint", Map.of(), false),
                () -> reopen(player, () -> showRelationEditor(player, ListType.WHITELIST))));
        buttons.add(button(player, plugin.messages().component("ui.settings.manage-blacklist", Map.of(), false),
                plugin.messages().component("ui.settings.blacklist-hint", Map.of(), false),
                () -> reopen(player, () -> showRelationEditor(player, ListType.BLACKLIST))));
        buttons.add(saveModeButton(player));

        ActionButton close = button(player, plugin.messages().component(
                "ui.button.close", Map.of(), false), null, () -> { });
        DialogBase base = new DialogBase(text(plugin.messages().component(
                "ui.settings.title", Map.of(), false)))
                .body(List.of(new PlainMessageBody(text(plugin.messages().component(
                        "ui.settings.list-counts", Map.of(
                                "whitelist", Integer.toString(plugin.settings().relations(
                                        player.getUniqueId(), ListType.WHITELIST).size()),
                                "blacklist", Integer.toString(plugin.settings().relations(
                                        player.getUniqueId(), ListType.BLACKLIST).size())), false)), 400)))
                .inputs(List.<DialogInput>of(modeInput))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.AfterAction.CLOSE);
        player.showDialog(new MultiActionDialog(base, buttons, 2, close));
    }

    /** 向名单管理 Body 添加标题及每名成员的独立可点击行。 */
    private void addRelationBody(List<DialogBody> body, Player player, ListType type, String titleKey) {
        body.add(new PlainMessageBody(text(plugin.messages().component(titleKey, Map.of(), false)), 400));
        List<RelationEntry> entries = plugin.settings().relations(player.getUniqueId(), type);
        if (entries.isEmpty()) {
            body.add(new PlainMessageBody(text(plugin.messages().component(
                    "ui.settings.list-empty", Map.of(), false)), 400));
            return;
        }
        for (RelationEntry entry : entries) {
            UUID targetId = entry.targetId();
            String targetName = entry.targetName();
            TextComponent line = new TextComponent(text(Component.text(targetName)));
            line.addExtra(" ");
            line.addExtra(clickable(player,
                    plugin.messages().component("ui.settings.remove", Map.of(), false),
                    plugin.messages().component("ui.settings.remove-hover", Map.of("player", targetName), false),
                    () -> {
                        plugin.settings().removeRelation(player.getUniqueId(), targetId, type);
                        plugin.messages().send(player, "relation-removed", Map.of(
                                "player", targetName,
                                "list", plugin.messages().text(type.languageKey())));
                        reopen(player, () -> showRelationEditor(player, type));
                    }));
            body.add(new PlainMessageBody(line, 400));
        }
    }

    /** 显示某一名单的现有成员、快捷移除入口和添加成员按钮。 */
    @Override
    public void showRelationEditor(Player player, ListType type) {
        clearCallbacks(player.getUniqueId());
        String listName = plugin.messages().text(type.languageKey());
        List<DialogBody> body = new ArrayList<>();
        addRelationBody(body, player, type,
                type == ListType.WHITELIST ? "ui.settings.whitelist-title" : "ui.settings.blacklist-title");
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(player, plugin.messages().component("ui.relation.add-member", Map.of(), false), null,
                () -> reopen(player, () -> showRelationPlayerSelector(player, type))));
        buttons.add(button(player, plugin.messages().component("ui.relation.back-settings", Map.of(), false), null,
                () -> reopen(player, () -> showSettings(player))));
        showActionDialog(player, plugin.messages().component("ui.relation.title",
                Map.of("list", listName), false), body, buttons, 2);
    }

    /** 显示二级在线玩家列表，用于向指定名单添加新成员。 */
    private void showRelationPlayerSelector(Player player, ListType type) {
        clearCallbacks(player.getUniqueId());
        List<ActionButton> buttons = Bukkit.getOnlinePlayers().stream()
                .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
                .filter(target -> !plugin.settings().hasRelation(
                        player.getUniqueId(), target.getUniqueId(), type))
                .sorted((first, second) -> first.getName().compareToIgnoreCase(second.getName()))
                .map(target -> {
                    KnownPlayer knownTarget = plugin.settings().knownPlayer(target);
                    return button(player, Component.text(target.getName()), null, () -> {
                        plugin.settings().setRelation(player.getUniqueId(), knownTarget, type);
                        plugin.messages().send(player, "relation-added", Map.of(
                                "player", knownTarget.name(),
                                "list", plugin.messages().text(type.languageKey())));
                        reopen(player, () -> showRelationEditor(player, type));
                    });
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        buttons.add(button(player, plugin.messages().component("ui.relation.back-list", Map.of(), false), null,
                () -> reopen(player, () -> showRelationEditor(player, type))));
        String listName = plugin.messages().text(type.languageKey());
        Component bodyText = plugin.messages().component(
                buttons.size() == 1 ? "ui.relation.select-empty" : "ui.relation.select-hint",
                Map.of("list", listName), false);
        showActionDialog(player, plugin.messages().component("ui.relation.select-title",
                Map.of("list", listName), false), bodyText, buttons, 3);
    }

    /** 向聊天框发送可点击的同意与拒绝文本，复用命令路径。 */
    private void showChatActions(Player receiver, Player sender, TeleportRequest request, String messageKey) {
        TextComponent message = new TextComponent(text(plugin.messages().component(
                messageKey, Map.of("player", sender.getName()), true)));
        message.addExtra("  ");
        message.addExtra(command(
                plugin.messages().component("ui.chat.accept", Map.of(), false),
                plugin.messages().component("ui.chat.accept-hover", Map.of(), false),
                "/tpaccept " + request.id()));
        message.addExtra("  ");
        message.addExtra(command(
                plugin.messages().component("ui.chat.deny", Map.of(), false),
                plugin.messages().component("ui.chat.deny-hover", Map.of(), false),
                "/tpdeny " + request.id()));
        receiver.spigot().sendMessage(message);
    }

    /** 显示通用多按钮 Dialog，无按钮时改用单个关闭按钮。 */
    private void showActionDialog(Player player, Component title, Component body,
                                  List<ActionButton> actions, int columns) {
        showActionDialog(player, title, List.<DialogBody>of(new PlainMessageBody(text(body), 400)), actions, columns);
    }

    /** 显示支持多行 Body 元素的通用多按钮 Dialog。 */
    private void showActionDialog(Player player, Component title, List<DialogBody> body,
                                  List<ActionButton> actions, int columns) {
        DialogBase base = new DialogBase(text(title))
                .body(body)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.AfterAction.CLOSE);
        if (actions.isEmpty()) {
            ActionButton close = button(player, plugin.messages().component(
                    "ui.button.close", Map.of(), false), null, () -> { });
            player.showDialog(new NoticeDialog(base, close));
            return;
        }
        ActionButton exit = button(player, plugin.messages().component(
                "ui.button.close", Map.of(), false), null, () -> { });
        player.showDialog(new MultiActionDialog(base, actions, columns, exit));
    }

    /** 创建带一次性回调的 Dialog 底部按钮。 */
    private ActionButton button(Player expectedPlayer, Component label, Component tooltip, Runnable action) {
        BaseComponent tooltipComponent = tooltip == null ? null : text(tooltip);
        String callbackId = registerCallback(expectedPlayer, action);
        return new ActionButton(text(label), tooltipComponent, null, new CustomClickAction(callbackId));
    }

    /** 创建把 dropdown 选值持久化为接受模式的保存按钮。 */
    private ActionButton saveModeButton(Player player) {
        return new ActionButton(
                text(plugin.messages().component("ui.settings.save", Map.of(), false)), null, null,
                new CustomClickAction(registerCallback(player, (clicked, payload) -> {
                    String selectedMode = primitiveString(payload, ACCEPT_MODE_INPUT);
                    AcceptMode mode = selectedMode == null ? null : AcceptMode.parse(selectedMode);
                    if (mode == null) {
                        return;
                    }
                    plugin.settings().setMode(clicked, mode);
                    plugin.messages().send(clicked, "mode-updated",
                            Map.of("mode", plugin.messages().text(mode.languageKey())));
                    clearCallbacks(clicked.getUniqueId());
                })));
    }

    /** 创建 Dialog body 内联的可点击回调文本（同意 / 拒绝 / 移除）。 */
    private TextComponent clickable(Player expectedPlayer, Component label, Component hover, Runnable action) {
        TextComponent component = new TextComponent(text(label));
        if (hover != null) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new BaseComponent[]{text(hover)})));
        }
        component.setClickEvent(new ClickEventCustom(registerCallback(expectedPlayer, action), ""));
        return component;
    }

    /** 创建运行命令的可点击聊天文本，复用 /tpaccept、/tpdeny 命令路径。 */
    private TextComponent command(Component label, Component hover, String runCommand) {
        TextComponent component = new TextComponent(text(label));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new BaseComponent[]{text(hover)})));
        component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, runCommand));
        return component;
    }

    /** 注册一个仅允许指定玩家在有效期内消费一次的可信回调。 */
    private String registerCallback(Player player, Runnable action) {
        return registerCallback(player, (clicked, payload) -> action.run());
    }

    /** 注册一个可读取 Dialog 输入的可信回调。 */
    private String registerCallback(Player player, CallbackAction action) {
        String key = new NamespacedKey(plugin,
                "dialog_" + Long.toString(callbackSequence.incrementAndGet(), 36)).toString();
        int lifetime = Math.max(300, plugin.getConfig().getInt("request-timeout-seconds", 30) + 5);
        CallbackSession session = new CallbackSession(player.getUniqueId(), action,
                System.currentTimeMillis() + lifetime * 1000L);
        callbacks.put(key, session);
        playerCallbacks.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(key);
        return key;
    }

    /** 从 Spigot custom-click JSON 中安全读取基础字符串输入。 */
    private String primitiveString(JsonElement payload, String key) {
        if (payload == null || !payload.isJsonObject()) {
            return null;
        }
        JsonElement value = payload.getAsJsonObject().get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsString();
    }

    /** 移除一个已触发或已失效的回调，并同步玩家索引。 */
    private CallbackSession removeCallback(String key) {
        CallbackSession session = callbacks.remove(key);
        if (session == null) {
            return null;
        }
        Set<String> keys = playerCallbacks.get(session.playerId());
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                playerCallbacks.remove(session.playerId(), keys);
            }
        }
        return session;
    }

    /** 清理玩家当前 Dialog 注册的全部未消费回调。 */
    private void clearCallbacks(UUID playerId) {
        Set<String> keys = playerCallbacks.remove(playerId);
        if (keys != null) {
            keys.forEach(callbacks::remove);
        }
    }

    /** 使用 Spigot Bungee Chat API 发送 Adventure 富文本。 */
    private void send(Player player, Component message) {
        player.spigot().sendMessage(BungeeComponentSerializer.get().serialize(message));
    }

    /** 将 Adventure 组件转换为 Spigot Bungee 组件。 */
    private BaseComponent text(Component component) {
        return new TextComponent(BungeeComponentSerializer.get().serialize(component));
    }

    /** 下一刻重新打开界面，避开当前按钮触发后的客户端自动关闭。 */
    private void reopen(Player player, Runnable opener) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                opener.run();
            }
        });
    }
}
