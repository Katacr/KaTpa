package org.katacr.katpa.ui.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.KnownPlayer;
import org.katacr.katpa.model.ListType;
import org.katacr.katpa.model.NetworkPlayer;
import org.katacr.katpa.model.RelationEntry;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.model.TeleportRequest;
import org.katacr.katpa.model.Home;
import org.katacr.katpa.model.Warp;
import org.katacr.katpa.ui.InteractionPlatform;
import org.katacr.katpa.ui.InteractionService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 使用 Paper 原生 Dialog、Adventure 可点击文本回调构建 KaTpa 的交互界面。 */
public final class PaperInteractionPlatform implements InteractionPlatform, InteractionService.PlatformNamed {
    private static final String ACCEPT_MODE_INPUT = "accept_mode";
    private KaTpaPlugin plugin;
    private final Set<UUID> requestListViewers = new HashSet<>();
    private boolean warnedDialogCloseFailure;

    /** 返回该适配器所用平台名称。 */
    @Override
    public String platformName() {
        return "Paper";
    }

    /** 绑定插件服务，供 Dialog 回调调用业务逻辑。 */
    @Override
    public void initialize(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 使用 Paper Adventure API 向玩家发送 ActionBar。 */
    @Override
    public void sendActionBar(Player player, Component message) {
        player.sendActionBar(message);
    }

    /** 使用 Adventure 命令点击事件发送带请求 UUID 的撤销入口。 */
    @Override
    public void sendCancellableRequestCreated(Player sender, String receiverName, TeleportRequest request,
                                              String messageKey) {
        Component cancel = plugin.messages().component("ui.chat.cancel", Map.of(), false)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpacancel " + request.id()))
                .hoverEvent(HoverEvent.showText(plugin.messages().component(
                        "ui.chat.cancel-hover", Map.of(), false)));
        sender.sendMessage(plugin.messages().component(
                        messageKey, Map.of("player", receiverName), true)
                .append(Component.text("  "))
                .append(cancel));
    }

    /** 按接收者偏好显示一条待处理请求。 */
    @Override
    public void presentRequest(Player receiver, String senderName, TeleportRequest request, AcceptMode mode) {
        String messageKey = request.type() == RequestType.TPA
                ? "request-received-tpa" : "request-received-here";
        switch (mode) {
            case DIALOG -> showRequestList(receiver);
            case CHAT -> showChatActions(receiver, senderName, request, messageKey);
            case SNEAK -> receiver.sendMessage(plugin.messages().component(
                            messageKey, Map.of("player", senderName), true)
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
            closeRequestListDialog(receiver);
            return;
        }
        renderRequestList(receiver);
    }

    /** 停止指定玩家后续接收请求列表变化推送。 */
    @Override
    public void stopRequestList(UUID playerId) {
        requestListViewers.remove(playerId);
    }

    /** 在插件关闭时清除全部请求列表查看状态。 */
    @Override
    public void shutdown() {
        requestListViewers.clear();
    }

    /** 构建带上下文同意/拒绝文本的请求列表并直接替换当前 Dialog。 */
    private void renderRequestList(Player receiver) {
        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> fallbackButtons = new ArrayList<>();
        body.add(DialogBody.plainMessage(plugin.messages().component(
                "ui.request-list.header", Map.of(), false), 500));
        for (TeleportRequest request : plugin.requests().incoming(receiver.getUniqueId())) {
            String senderName = plugin.requests().senderName(request);
            String messageKey = request.type() == RequestType.TPA
                    ? "ui.request-list.tpa" : "ui.request-list.here";
            Component accept = plugin.messages().component("ui.button.accept-bracket", Map.of(), false)
                    .hoverEvent(HoverEvent.showText(plugin.messages().component(
                            "ui.chat.accept-hover", Map.of(), false)))
                    .clickEvent(ClickEvent.callback(audience -> {
                        if (audience instanceof Player clicked
                                && clicked.getUniqueId().equals(receiver.getUniqueId())) {
                            Bukkit.getScheduler().runTask(plugin,
                                    () -> plugin.requests().accept(receiver, request.id()));
                        }
                    }, requestCallbackOptions()));
            Component deny = plugin.messages().component("ui.button.deny-bracket", Map.of(), false)
                    .hoverEvent(HoverEvent.showText(plugin.messages().component(
                            "ui.chat.deny-hover", Map.of(), false)))
                    .clickEvent(ClickEvent.callback(audience -> {
                        if (audience instanceof Player clicked
                                && clicked.getUniqueId().equals(receiver.getUniqueId())) {
                            Bukkit.getScheduler().runTask(plugin,
                                    () -> plugin.requests().deny(receiver, request.id()));
                        }
                    }, requestCallbackOptions()));
            Component line = plugin.messages().component(messageKey, Map.of(
                            "player", senderName,
                            "seconds", Long.toString(plugin.requests().remainingSeconds(request))), false)
                    .append(Component.space())
                    .append(accept)
                    .append(Component.space())
                    .append(deny);
            body.add(DialogBody.plainMessage(line, 500));
            fallbackButtons.add(ActionButton.builder(plugin.messages().component(
                            "ui.request-list.accept-button", Map.of("player", senderName), false))
                    .action(callback(receiver, () -> plugin.requests().accept(receiver, request.id())))
                    .build());
        }
        ActionButton close = ActionButton.builder(plugin.messages().component(
                        "ui.button.close", Map.of(), false))
                .action(callback(receiver, () -> stopRequestList(receiver.getUniqueId())))
                .build();
        DialogBase base = DialogBase.builder(plugin.messages().component(
                        "ui.request-list.title", Map.of(), false))
                .body(body)
                .canCloseWithEscape(false)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        receiver.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(fallbackButtons).columns(1).exitAction(close).build())));
    }

    /** 最后一条请求消失时关闭请求 Dialog，并兼容 Paper 1.21.7 未公开关闭 API 的情况。 */
    private void closeRequestListDialog(Player receiver) {
        try {
            receiver.getClass().getMethod("closeDialog").invoke(receiver);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Object handle = receiver.getClass().getMethod("getHandle").invoke(receiver);
            java.lang.reflect.Field connectionField;
            try {
                connectionField = handle.getClass().getField("connection");
            } catch (NoSuchFieldException exception) {
                connectionField = handle.getClass().getDeclaredField("connection");
                connectionField.setAccessible(true);
            }
            Object connection = connectionField.get(handle);
            Class<?> packetClass = Class.forName(
                    "net.minecraft.network.protocol.common.ClientboundClearDialogPacket");
            Object packet = packetClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method sendMethod = java.util.Arrays.stream(connection.getClass().getMethods())
                    .filter(method -> method.getName().equals("send"))
                    .filter(method -> method.getParameterCount() == 1)
                    .filter(method -> method.getParameterTypes()[0].isAssignableFrom(packetClass))
                    .findFirst()
                    .orElseThrow(NoSuchMethodException::new);
            sendMethod.invoke(connection, packet);
        } catch (ReflectiveOperationException exception) {
            if (!warnedDialogCloseFailure) {
                warnedDialogCloseFailure = true;
                plugin.getLogger().warning("无法关闭已清空的请求 Dialog: " + exception.getMessage());
            }
            receiver.closeInventory();
        }
    }

    /** 显示未指定玩家参数时使用的在线玩家 Dialog 选择器。 */
    @Override
    public void showPlayerSelector(Player player, RequestType type) {
        List<ActionButton> buttons = plugin.network().onlinePlayers().stream()
                .filter(target -> !target.id().equals(player.getUniqueId()))
                .map(target -> ActionButton.builder(
                                Component.text(target.name(), NamedTextColor.AQUA))
                        .tooltip(plugin.messages().component(type == RequestType.TPA
                                ? "ui.selector.tooltip-tpa" : "ui.selector.tooltip-here", Map.of(), false))
                        .action(callback(player, () -> plugin.requests().create(player, target, type)))
                        .build())
                .toList();

        Component title = plugin.messages().component(type == RequestType.TPA
                ? "ui.selector.title-tpa" : "ui.selector.title-here", Map.of(), false);
        Component body = buttons.isEmpty()
                ? plugin.messages().component("ui.selector.empty", Map.of(), false)
                : plugin.messages().component("ui.selector.count",
                Map.of("count", Integer.toString(buttons.size())), false);
        showActionDialog(player, title, body, buttons, 3);
    }

    /** 显示用于选择接受模式和管理名单的个人设置 Dialog。 */
    @Override
    public void showSettings(Player player) {
        AcceptMode currentMode = plugin.settings().mode(player.getUniqueId());
        List<SingleOptionDialogInput.OptionEntry> modeOptions = new ArrayList<>();
        for (AcceptMode mode : AcceptMode.values()) {
            modeOptions.add(SingleOptionDialogInput.OptionEntry.create(
                    mode.name().toLowerCase(),
                    plugin.messages().component(mode.languageKey(), Map.of(), false),
                    mode == currentMode));
        }
        DialogInput modeInput = DialogInput.singleOption(
                        ACCEPT_MODE_INPUT,
                        plugin.messages().component("ui.settings.mode-label", Map.of(), false),
                        modeOptions)
                .width(300)
                .build();

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.settings.manage-whitelist", Map.of(), false))
                .tooltip(plugin.messages().component("ui.settings.whitelist-hint", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showRelationEditor(player, ListType.WHITELIST))))
                .build());
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.settings.manage-blacklist", Map.of(), false))
                .tooltip(plugin.messages().component("ui.settings.blacklist-hint", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showRelationEditor(player, ListType.BLACKLIST))))
                .build());
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.settings.save", Map.of(), false))
                .action(saveModeAction(player))
                .build());

        ActionButton close = ActionButton.builder(plugin.messages().component(
                        "ui.button.close", Map.of(), false))
                .action(callback(player, () -> { }))
                .build();
        DialogBase base = DialogBase.builder(plugin.messages().component(
                        "ui.settings.title", Map.of(), false))
                .body(List.of(DialogBody.plainMessage(plugin.messages().component(
                        "ui.settings.list-counts", Map.of(
                                "whitelist", Integer.toString(plugin.settings().relations(
                                        player.getUniqueId(), ListType.WHITELIST).size()),
                                "blacklist", Integer.toString(plugin.settings().relations(
                                        player.getUniqueId(), ListType.BLACKLIST).size())), false), 400)))
                .inputs(List.of(modeInput))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(buttons).columns(2).exitAction(close).build())));
    }

    /** 向名单管理 Body 添加标题及每名成员的独立可点击行。 */
    private void addRelationBody(List<DialogBody> body, Player player, ListType type, String titleKey) {
        body.add(DialogBody.plainMessage(plugin.messages().component(titleKey, Map.of(), false), 400));
        List<RelationEntry> entries = plugin.settings().relations(player.getUniqueId(), type);
        if (entries.isEmpty()) {
            body.add(DialogBody.plainMessage(plugin.messages().component(
                    "ui.settings.list-empty", Map.of(), false), 400));
            return;
        }
        for (RelationEntry entry : entries) {
            Component remove = plugin.messages().component("ui.settings.remove", Map.of(), false)
                    .hoverEvent(HoverEvent.showText(plugin.messages().component(
                            "ui.settings.remove-hover", Map.of("player", entry.targetName()), false)))
                    .clickEvent(ClickEvent.callback(audience -> {
                        if (audience instanceof Player clicked
                                && clicked.getUniqueId().equals(player.getUniqueId())) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.settings().removeRelation(player.getUniqueId(), entry.targetId(), type);
                                plugin.messages().send(player, "relation-removed", Map.of(
                                        "player", entry.targetName(),
                                        "list", plugin.messages().text(type.languageKey())));
                                reopen(player, () -> showRelationEditor(player, type));
                            });
                        }
                    }, callbackOptions()));
            Component line = Component.text(entry.targetName(), NamedTextColor.WHITE)
                    .append(Component.space())
                    .append(remove);
            body.add(DialogBody.plainMessage(line, 400));
        }
    }

    /** 显示某一名单的现有成员、快捷移除入口和添加成员按钮。 */
    @Override
    public void showRelationEditor(Player player, ListType type) {
        String listName = plugin.messages().text(type.languageKey());
        List<DialogBody> body = new ArrayList<>();
        addRelationBody(body, player, type,
                type == ListType.WHITELIST ? "ui.settings.whitelist-title" : "ui.settings.blacklist-title");
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.relation.add-member", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showRelationPlayerSelector(player, type))))
                .build());
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.relation.back-settings", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showSettings(player))))
                .build());
        showActionDialog(player, plugin.messages().component("ui.relation.title",
                Map.of("list", listName), false), body, buttons, 2);
    }

    /** 显示二级在线玩家列表，用于向指定名单添加新成员。 */
    private void showRelationPlayerSelector(Player player, ListType type) {
        List<ActionButton> buttons = Bukkit.getOnlinePlayers().stream()
                .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
                .filter(target -> !plugin.settings().hasRelation(
                        player.getUniqueId(), target.getUniqueId(), type))
                .sorted((first, second) -> first.getName().compareToIgnoreCase(second.getName()))
                .map(target -> {
                    KnownPlayer knownTarget = plugin.settings().knownPlayer(target);
                    return ActionButton.builder(Component.text(target.getName(), NamedTextColor.AQUA))
                            .action(callback(player, () -> {
                                plugin.settings().setRelation(player.getUniqueId(), knownTarget, type);
                                plugin.messages().send(player, "relation-added", Map.of(
                                        "player", knownTarget.name(),
                                        "list", plugin.messages().text(type.languageKey())));
                                reopen(player, () -> showRelationEditor(player, type));
                            }))
                            .build();
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.relation.back-list", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showRelationEditor(player, type))))
                .build());
        String listName = plugin.messages().text(type.languageKey());
        Component body = plugin.messages().component(
                buttons.size() == 1 ? "ui.relation.select-empty" : "ui.relation.select-hint",
                Map.of("list", listName), false);
        showActionDialog(player, plugin.messages().component("ui.relation.select-title",
                Map.of("list", listName), false), body, buttons, 3);
    }

    /** 显示地标选择 Dialog，列出玩家有权限使用的全部地标。 */
    @Override
    public void showWarpSelector(Player player) {
        List<ActionButton> buttons = plugin.warpStore().all().stream()
                .filter(warp -> warp.permission().isBlank() || player.hasPermission(warp.permission()))
                .map(warp -> ActionButton.builder(
                                Component.text(warp.name(), NamedTextColor.AQUA))
                        .tooltip(Component.text(warp.server(), NamedTextColor.GRAY))
                        .action(callback(player, () -> plugin.warp().warp(player, warp.name())))
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Component title = plugin.messages().component("ui.warp.selector-title", Map.of(), false);
        Component body = buttons.isEmpty()
                ? plugin.messages().component("ui.warp.empty", Map.of(), false)
                : plugin.messages().component("ui.warp.count",
                Map.of("count", Integer.toString(buttons.size())), false);
        showActionDialog(player, title, body, buttons, 3);
    }

    /** 显示管理员地标管理 Dialog，列出全部地标并提供编辑入口。 */
    @Override
    public void showWarpManager(Player player) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(plugin.messages().component(
                "ui.warp.manager-title", Map.of(), false), 400));
        List<ActionButton> buttons = new ArrayList<>();
        for (Warp warp : plugin.warpStore().all()) {
            buttons.add(ActionButton.builder(
                            Component.text(warp.name(), NamedTextColor.WHITE))
                    .tooltip(Component.text(warp.server() + " / " + warp.world(), NamedTextColor.GRAY))
                    .action(callback(player, () -> reopen(player, () -> showWarpEditor(player, warp))))
                    .build());
        }
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.warp.add", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showWarpCreateInput(player))))
                .build());
        showActionDialog(player, plugin.messages().component(
                "ui.warp.manager-title", Map.of(), false), body, buttons, 3);
    }

    /** 显示创建新地标的名称输入 Dialog。 */
    private void showWarpCreateInput(Player player) {
        DialogInput nameInput = DialogInput.text("warp_name",
                        plugin.messages().component("ui.warp.name-label", Map.of(), false))
                .maxLength(32)
                .build();
        ActionButton save = ActionButton.builder(plugin.messages().component(
                        "ui.warp.save", Map.of(), false))
                .action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player clicked)
                            || !clicked.getUniqueId().equals(player.getUniqueId())) {
                        return;
                    }
                    String name = response.getText("warp_name");
                    if (name == null || name.isBlank()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.warp().setWarp(player, name.trim());
                        reopen(player, () -> showWarpManager(player));
                    });
                }, callbackOptions()))
                .build();
        DialogBase base = DialogBase.builder(plugin.messages().component(
                        "ui.warp.create-title", Map.of(), false))
                .body(List.of(DialogBody.plainMessage(plugin.messages().component(
                        "ui.warp.create-hint", Map.of(), false), 400)))
                .inputs(List.of(nameInput))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.notice(save))));
    }

    /** 显示单个地标编辑 Dialog，可更新位置、权限、冷却和费用。 */
    @Override
    public void showWarpEditor(Player player, Warp warp) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                warp.name(), NamedTextColor.AQUA), 400));
        body.add(DialogBody.plainMessage(Component.text(
                "Server: " + warp.server() + "  World: " + warp.world(), NamedTextColor.GRAY), 400));
        body.add(DialogBody.plainMessage(Component.text(
                String.format("XYZ: %.1f / %.1f / %.1f", warp.x(), warp.y(), warp.z()),
                NamedTextColor.GRAY), 400));
        body.add(DialogBody.plainMessage(Component.text(
                "Permission: " + (warp.permission().isBlank() ? "(none)" : warp.permission()),
                NamedTextColor.GRAY), 400));
        body.add(DialogBody.plainMessage(Component.text(
                "Cooldown: " + warp.cooldownSeconds() + "s  Cost: " + String.format("%.2f", warp.cost()),
                NamedTextColor.GRAY), 400));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.warp.update-location", Map.of(), false))
                .action(callback(player, () -> {
                    plugin.warp().updateLocation(player, warp.name());
                    plugin.messages().send(player, "warp-location-updated",
                            Map.of("name", warp.name()));
                    reopen(player, () -> showWarpManager(player));
                }))
                .build());
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.warp.set-permission", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showWarpPermissionInput(player, warp))))
                .build());
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.warp.set-cooldown", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showWarpCooldownInput(player, warp))))
                .build());
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.warp.set-cost", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showWarpCostInput(player, warp))))
                .build());
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.warp.delete", Map.of(), false))
                .action(callback(player, () -> {
                    plugin.warp().delWarp(player, warp.name());
                    reopen(player, () -> showWarpManager(player));
                }))
                .build());
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.warp.back-list", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showWarpManager(player))))
                .build());
        showActionDialog(player, plugin.messages().component(
                "ui.warp.editor-title", Map.of("name", warp.name()), false), body, buttons, 2);
    }

    /** 显示权限输入 Dialog。 */
    private void showWarpPermissionInput(Player player, Warp warp) {
        DialogInput input = DialogInput.text("warp_permission",
                        plugin.messages().component("ui.warp.permission-label", Map.of(), false))
                .maxLength(128)
                .build();
        ActionButton save = ActionButton.builder(plugin.messages().component(
                        "ui.warp.save", Map.of(), false))
                .action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player clicked)
                            || !clicked.getUniqueId().equals(player.getUniqueId())) return;
                    String perm = response.getText("warp_permission");
                    if (perm == null) perm = "";
                    String finalPerm = perm.trim();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.warp().setPermission(warp.name(), finalPerm);
                        plugin.messages().send(player, "warp-permission-updated",
                                Map.of("name", warp.name(), "permission", finalPerm));
                        reopen(player, () -> showWarpEditor(player, plugin.warpStore().find(warp.name())));
                    });
                }, callbackOptions()))
                .build();
        DialogBase base = DialogBase.builder(plugin.messages().component(
                        "ui.warp.permission-title", Map.of("name", warp.name()), false))
                .body(List.of(DialogBody.plainMessage(plugin.messages().component(
                        "ui.warp.permission-hint", Map.of(), false), 400)))
                .inputs(List.of(input))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.notice(save))));
    }

    /** 显示冷却输入 Dialog。 */
    private void showWarpCooldownInput(Player player, Warp warp) {
        DialogInput input = DialogInput.text("warp_cooldown",
                        plugin.messages().component("ui.warp.cooldown-label", Map.of(), false))
                .maxLength(10)
                .build();
        ActionButton save = ActionButton.builder(plugin.messages().component(
                        "ui.warp.save", Map.of(), false))
                .action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player clicked)
                            || !clicked.getUniqueId().equals(player.getUniqueId())) return;
                    String valueStr = response.getText("warp_cooldown");
                    if (valueStr == null) return;
                    try {
                        int cooldown = Integer.parseInt(valueStr.trim());
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.warp().setCooldown(warp.name(), Math.max(0, cooldown));
                            plugin.messages().send(player, "warp-cooldown-updated",
                                    Map.of("name", warp.name(), "seconds", Integer.toString(Math.max(0, cooldown))));
                            reopen(player, () -> showWarpEditor(player, plugin.warpStore().find(warp.name())));
                        });
                    } catch (NumberFormatException ignored) {
                    }
                }, callbackOptions()))
                .build();
        DialogBase base = DialogBase.builder(plugin.messages().component(
                        "ui.warp.cooldown-title", Map.of("name", warp.name()), false))
                .body(List.of())
                .inputs(List.of(input))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.notice(save))));
    }

    /** 显示费用输入 Dialog。 */
    private void showWarpCostInput(Player player, Warp warp) {
        DialogInput input = DialogInput.text("warp_cost",
                        plugin.messages().component("ui.warp.cost-label", Map.of(), false))
                .maxLength(10)
                .build();
        ActionButton save = ActionButton.builder(plugin.messages().component(
                        "ui.warp.save", Map.of(), false))
                .action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player clicked)
                            || !clicked.getUniqueId().equals(player.getUniqueId())) return;
                    String valueStr = response.getText("warp_cost");
                    if (valueStr == null) return;
                    try {
                        double cost = Double.parseDouble(valueStr.trim());
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.warp().setCost(warp.name(), Math.max(0, cost));
                            plugin.messages().send(player, "warp-cost-updated",
                                    Map.of("name", warp.name(), "cost", String.format("%.2f", Math.max(0, cost))));
                            reopen(player, () -> showWarpEditor(player, plugin.warpStore().find(warp.name())));
                        });
                    } catch (NumberFormatException ignored) {
                    }
                }, callbackOptions()))
                .build();
        DialogBase base = DialogBase.builder(plugin.messages().component(
                        "ui.warp.cost-title", Map.of("name", warp.name()), false))
                .body(List.of())
                .inputs(List.of(input))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.notice(save))));
    }

    /** 显示玩家个人家选择 Dialog，列出玩家全部家。 */
    @Override
    public void showHomeSelector(Player player) {
        List<ActionButton> buttons = plugin.homeStore().all(player.getUniqueId()).stream()
                .map(home -> ActionButton.builder(
                                Component.text(home.name(), NamedTextColor.AQUA))
                        .tooltip(Component.text(home.server(), NamedTextColor.GRAY))
                        .action(callback(player, () -> plugin.home().home(player, home.name())))
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Component title = plugin.messages().component("ui.home.selector-title", Map.of(), false);
        Component body = buttons.isEmpty()
                ? plugin.messages().component("ui.home.empty", Map.of(), false)
                : plugin.messages().component("ui.home.count",
                Map.of("count", Integer.toString(buttons.size())), false);
        showActionDialog(player, title, body, buttons, 3);
    }

    /** 显示玩家个人家管理 Dialog，列出全部家并提供创建和删除入口。 */
    @Override
    public void showHomeManager(Player player) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(plugin.messages().component(
                "ui.home.manager-title", Map.of(), false), 400));
        List<ActionButton> buttons = new ArrayList<>();
        for (Home home : plugin.homeStore().all(player.getUniqueId())) {
            buttons.add(ActionButton.builder(
                            Component.text(home.name(), NamedTextColor.WHITE))
                    .tooltip(Component.text(home.server() + " / " + home.world(), NamedTextColor.GRAY))
                    .action(callback(player, () -> {
                        plugin.home().delHome(player, home.name());
                        reopen(player, () -> showHomeManager(player));
                    }))
                    .build());
        }
        buttons.add(ActionButton.builder(plugin.messages().component(
                        "ui.home.add", Map.of(), false))
                .action(callback(player, () -> reopen(player, () -> showHomeCreateInput(player))))
                .build());
        showActionDialog(player, plugin.messages().component(
                "ui.home.manager-title", Map.of(), false), body, buttons, 3);
    }

    /** 显示创建新家的名称输入 Dialog。 */
    private void showHomeCreateInput(Player player) {
        DialogInput nameInput = DialogInput.text("home_name",
                        plugin.messages().component("ui.home.name-label", Map.of(), false))
                .maxLength(32)
                .build();
        ActionButton save = ActionButton.builder(plugin.messages().component(
                        "ui.home.save", Map.of(), false))
                .action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player clicked)
                            || !clicked.getUniqueId().equals(player.getUniqueId())) {
                        return;
                    }
                    String name = response.getText("home_name");
                    if (name == null || name.isBlank()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.home().setHome(player, name.trim());
                        reopen(player, () -> showHomeManager(player));
                    });
                }, callbackOptions()))
                .build();
        DialogBase base = DialogBase.builder(plugin.messages().component(
                        "ui.home.create-title", Map.of(), false))
                .body(List.of(DialogBody.plainMessage(plugin.messages().component(
                        "ui.home.create-hint", Map.of(), false), 400)))
                .inputs(List.of(nameInput))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.notice(save))));
    }

    /** 向聊天框发送可点击的同意与拒绝文本。 */
    private void showChatActions(Player receiver, String senderName, TeleportRequest request, String messageKey) {        Component accept = plugin.messages().component("ui.chat.accept", Map.of(), false)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpaccept " + request.id()))
                .hoverEvent(HoverEvent.showText(plugin.messages().component(
                        "ui.chat.accept-hover", Map.of(), false)));
        Component deny = plugin.messages().component("ui.chat.deny", Map.of(), false)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpdeny " + request.id()))
                .hoverEvent(HoverEvent.showText(plugin.messages().component(
                        "ui.chat.deny-hover", Map.of(), false)));
        receiver.sendMessage(plugin.messages().component(
                        messageKey, Map.of("player", senderName), true)
                .append(Component.text("  "))
                .append(accept)
                .append(Component.text("  "))
                .append(deny));
    }

    /** 显示通用多按钮 Dialog，无按钮时改用单个关闭按钮。 */
    private void showActionDialog(Player player, Component title, Component body,
                                  List<ActionButton> actions, int columns) {
        showActionDialog(player, title, List.of(DialogBody.plainMessage(body, 400)), actions, columns);
    }

    /** 显示支持多行 Body 元素的通用多按钮 Dialog。 */
    private void showActionDialog(Player player, Component title, List<DialogBody> body,
                                  List<ActionButton> actions, int columns) {
        DialogBase base = base(title, body);
        if (actions.isEmpty()) {
            ActionButton close = ActionButton.builder(plugin.messages().component(
                            "ui.button.close", Map.of(), false))
                    .action(callback(player, () -> { }))
                    .build();
            player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.notice(close))));
            return;
        }
        ActionButton exit = ActionButton.builder(plugin.messages().component(
                        "ui.button.close", Map.of(), false))
                .action(callback(player, () -> { }))
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(actions).columns(columns).exitAction(exit).build())));
    }

    /** 创建统一设置的 Dialog 基础信息。 */
    private DialogBase base(Component title, Component body) {
        return base(title, List.of(DialogBody.plainMessage(body, 400)));
    }

    /** 使用现成 Body 元素列表创建统一设置的 Dialog 基础信息。 */
    private DialogBase base(Component title, List<DialogBody> body) {
        return DialogBase.builder(title)
                .body(body)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
    }

    /** 创建读取 dropdown 单选值并持久化接受模式的保存动作。 */
    private DialogAction saveModeAction(Player expectedPlayer) {
        return DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player clicked)
                    || !clicked.getUniqueId().equals(expectedPlayer.getUniqueId())) {
                return;
            }
            String selectedMode = response.getText(ACCEPT_MODE_INPUT);
            if (selectedMode == null) {
                return;
            }
            AcceptMode mode = AcceptMode.parse(selectedMode);
            if (mode == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.settings().setMode(expectedPlayer, mode);
                plugin.messages().send(expectedPlayer, "mode-updated",
                        Map.of("mode", plugin.messages().text(mode.languageKey())));
            });
        }, callbackOptions());
    }

    /** 创建限单次触发且验证玩家身份的服务器端 Dialog 回调。 */
    private DialogAction callback(Player expectedPlayer, Runnable action) {
        return DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player clicked
                    && clicked.getUniqueId().equals(expectedPlayer.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, action);
            }
        }, callbackOptions());
    }

    /** 创建设置与请求交互共用的单次回调生命周期参数。 */
    private ClickCallback.Options callbackOptions() {
        int lifetime = Math.max(300, plugin.getConfig().getInt("modules.tpa.request-timeout-seconds", 30) + 5);
        return ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofSeconds(lifetime))
                .build();
    }

    /** 创建仅覆盖请求有效期的列表文本回调参数。 */
    private ClickCallback.Options requestCallbackOptions() {
        int lifetime = Math.max(5, plugin.getConfig().getInt("modules.tpa.request-timeout-seconds", 30) + 5);
        return ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofSeconds(lifetime))
                .build();
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
