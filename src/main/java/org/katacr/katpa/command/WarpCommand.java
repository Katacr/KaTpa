package org.katacr.katpa.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;

import java.util.List;
import java.util.Locale;

/** 处理 /warp <名称> [玩家] 地标传送指令和 Tab 补全。 */
public final class WarpCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的地标传送指令执行器。 */
    public WarpCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 无参数时打开地标选择菜单；管理员或控制台可指定本服在线玩家传送。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "player-only");
                return true;
            }
            if (!player.hasPermission("katpa.warp.menu")) {
                plugin.messages().send(player, "no-permission");
                return true;
            }
            plugin.interactions().showWarpSelector(player);
            return true;
        }
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "warp-command-usage");
                return true;
            }
            plugin.warp().warp(player, args[0]);
            return true;
        }
        if (args.length != 2) {
            plugin.messages().send(sender, "warp-command-usage");
            return true;
        }
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("katpa.warp.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", java.util.Map.of("player", args[1]));
            return true;
        }
        plugin.warp().warp(target, args[0]);
        return true;
    }

    /** 按现有地标名称补全，仅返回玩家有权限使用的。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return plugin.warpStore().all().stream()
                    .filter(w -> w.permission().isBlank()
                            || !(sender instanceof Player player)
                            || player.hasPermission(w.permission()))
                    .map(org.katacr.katpa.model.Warp::name)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length != 2 || (!(sender instanceof ConsoleCommandSender)
                && !sender.hasPermission("katpa.warp.admin"))) {
            return List.of();
        }
        String prefix = args[1].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
