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

/** 处理 /pw <名称> 玩家地标快捷传送指令和 Tab 补全。 */
public final class PwCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的玩家地标快捷传送指令执行器。 */
    public PwCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 无参数时打开玩家地标排行榜；否则传送到指定玩家地标。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.interactions().showPwarpLeaderboard(player);
            return true;
        }
        plugin.playerWarp().warp(player, args[0]);
        return true;
    }

    /** 按现有玩家地标名称补全。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || plugin.playerWarpStore() == null) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.playerWarpStore().all().stream()
                .map(org.katacr.katpa.model.PlayerWarp::name)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
