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

/** 处理 /setwarp 创建/更新地标和 /delwarp 删除地标的管理指令。 */
public final class SetWarpCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;
    private final boolean delete;

    /** 创建地标管理指令执行器；delete=true 时为删除模式。 */
    public SetWarpCommand(KaTpaPlugin plugin, boolean delete) {
        this.plugin = plugin;
        this.delete = delete;
    }

    /** /setwarp <名称> 创建或更新；/delwarp <名称> 删除；无参数时打开管理 Dialog。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.interactions().showWarpManager(player);
            return true;
        }
        if (delete) {
            plugin.warp().delWarp(player, args[0]);
        } else {
            plugin.warp().setWarp(player, args[0]);
        }
        return true;
    }

    /** 按现有地标名称补全。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.warpStore().names().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
