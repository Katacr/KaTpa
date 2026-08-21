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

/** 处理 /sethome 创建/更新家和 /delhome 删除家的指令。 */
public final class SetHomeCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;
    private final boolean delete;

    /** 创建家管理指令执行器；delete=true 时为删除模式。 */
    public SetHomeCommand(KaTpaPlugin plugin, boolean delete) {
        this.plugin = plugin;
        this.delete = delete;
    }

    /** /sethome <名称> 创建或更新；/delhome <名称> 删除；无参数时打开管理 Dialog。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.interactions().showHomeManager(player);
            return true;
        }
        if (delete) {
            plugin.home().delHome(player, args[0]);
        } else {
            plugin.home().setHome(player, args[0]);
        }
        return true;
    }

    /** 按玩家现有家名称补全。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.homeStore().names(player.getUniqueId()).stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
