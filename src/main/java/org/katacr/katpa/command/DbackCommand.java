package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.LocationRecord;

import java.util.List;
import java.util.Locale;

/** 处理 /dback 返回死亡位置指令，支持序号选择。 */
public final class DbackCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的死亡返回指令执行器。 */
    public DbackCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 无参数返回最近死亡位置，带序号返回对应槽位。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        int slot = 1;
        if (args.length > 0) {
            try {
                slot = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                plugin.messages().send(player, "dback-invalid-slot");
                return true;
            }
        }
        plugin.dback().dback(player, slot);
        return true;
    }

    /** 按玩家已有死亡位置数量补全序号，并附带时间提示。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        List<LocationRecord> records = plugin.dback().deathLocations(player);
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return java.util.stream.IntStream.rangeClosed(1, records.size())
                .mapToObj(Integer::toString)
                .filter(value -> value.startsWith(prefix))
                .toList();
    }
}
