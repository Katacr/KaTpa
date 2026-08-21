package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;

import java.util.List;
import java.util.Locale;

/** 处理 /warp <名称> 玩家传送指令和 Tab 补全。 */
public final class WarpCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的地标传送指令执行器。 */
    public WarpCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 无参数时打开地标选择 Dialog，有参数时传送到指定地标。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.interactions().showWarpSelector(player);
            return true;
        }
        plugin.warp().warp(player, args[0]);
        return true;
    }

    /** 按现有地标名称补全，仅返回玩家有权限使用的。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.warpStore().all().stream()
                .filter(warp -> warp.permission().isBlank()
                        || !(sender instanceof Player player)
                        || player.hasPermission(warp.permission()))
                .map(org.katacr.katpa.model.Warp::name)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
