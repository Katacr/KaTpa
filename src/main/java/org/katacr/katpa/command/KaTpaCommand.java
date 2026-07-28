package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 处理 /katap help 与 /katap reload 主命令。 */
public final class KaTpaCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件配置和语言服务的主命令。 */
    public KaTpaCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 显示帮助，或在权限允许时重载配置和语言文件。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("katpa.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            plugin.network().shutdown();
            plugin.reloadConfig();
            plugin.messages().reload();
            plugin.network().initialize();
            plugin.messages().send(sender, "config-reloaded");
            return true;
        }
        sendHelp(sender);
        return true;
    }

    /** 补全主命令的 help 与 reload 子命令。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("help", "reload").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }

    /** 从语言文件逐行发送完整的玩家指令帮助。 */
    private void sendHelp(CommandSender sender) {
        plugin.messages().sendComponent(sender,
                plugin.messages().component("help.header", Map.of(), false));
        for (String key : List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "settings", "reload")) {
            plugin.messages().sendComponent(sender,
                    plugin.messages().component("help." + key, Map.of(), false));
        }
    }
}
