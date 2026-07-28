package org.katacr.katpa.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.AcceptMode;
import org.katacr.katpa.model.KnownPlayer;
import org.katacr.katpa.model.ListType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 处理接受模式、名单维护、设置 Dialog 与管理员配置重载。 */
public final class SettingsCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件数据和界面的设置指令。 */
    public SettingsCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 按参数执行设置界面、模式、名单或重载操作。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("katpa.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            plugin.reloadConfig();
            plugin.messages().reload();
            plugin.messages().send(sender, "config-reloaded");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.interactions().showSettings(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("mode")) {
            return handleMode(player, args);
        }
        ListType type = parseListType(args[0]);
        if (type != null) {
            return handleList(player, type, args);
        }
        sendUsage(player);
        return true;
    }

    /** 根据参数位置补全设置子指令、模式、操作和已知玩家名称。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("mode", "whitelist", "blacklist", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return filter(List.of("dialog", "chat", "sneak"), args[1]);
        }
        if (args.length == 2 && parseListType(args[0]) != null) {
            return filter(List.of("add", "remove"), args[1]);
        }
        if (args.length == 3 && parseListType(args[0]) != null) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    /** 更新玩家接受模式，参数错误时显示用法。 */
    private boolean handleMode(Player player, String[] args) {
        if (args.length != 2) {
            sendUsage(player);
            return true;
        }
        AcceptMode mode = AcceptMode.parse(args[1]);
        if (mode == null) {
            sendUsage(player);
            return true;
        }
        plugin.settings().setMode(player, mode);
        plugin.messages().send(player, "mode-updated",
                Map.of("mode", plugin.messages().text(mode.languageKey())));
        return true;
    }

    /** 打开名单界面，或按 add/remove 参数精确修改名单。 */
    private boolean handleList(Player player, ListType type, String[] args) {
        if (args.length == 1) {
            plugin.interactions().showRelationEditor(player, type);
            return true;
        }
        if (args.length != 3 || (!args[1].equalsIgnoreCase("add") && !args[1].equalsIgnoreCase("remove"))) {
            sendUsage(player);
            return true;
        }
        Player onlineTarget = Bukkit.getPlayerExact(args[2]);
        KnownPlayer target = onlineTarget == null
                ? plugin.settings().findKnownPlayer(args[2])
                : plugin.settings().knownPlayer(onlineTarget);
        if (target == null) {
            plugin.messages().send(player, "known-player-not-found", Map.of("player", args[2]));
            return true;
        }
        if (target.uuid().equals(player.getUniqueId())) {
            plugin.messages().send(player, "relation-self");
            return true;
        }
        if (args[1].equalsIgnoreCase("add")) {
            plugin.settings().setRelation(player.getUniqueId(), target, type);
            plugin.messages().send(player, "relation-added",
                    Map.of("player", target.name(), "list", plugin.messages().text(type.languageKey())));
        } else {
            plugin.settings().removeRelation(player.getUniqueId(), target.uuid(), type);
            plugin.messages().send(player, "relation-removed",
                    Map.of("player", target.name(), "list", plugin.messages().text(type.languageKey())));
        }
        return true;
    }

    /** 将英文或中文名单参数转换为名单类型。 */
    private ListType parseListType(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "whitelist", "white", "白名单" -> ListType.WHITELIST;
            case "blacklist", "black", "黑名单" -> ListType.BLACKLIST;
            default -> null;
        };
    }

    /** 发送紧凑的设置指令帮助。 */
    private void sendUsage(Player player) {
        plugin.messages().sendComponent(player,
                plugin.messages().component("ui.usage.title", Map.of(), false));
        plugin.messages().sendComponent(player,
                plugin.messages().component("ui.usage.mode", Map.of(), false));
        plugin.messages().sendComponent(player,
                plugin.messages().component("ui.usage.list", Map.of(), false));
    }

    /** 按当前输入前缀过滤补全候选。 */
    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
