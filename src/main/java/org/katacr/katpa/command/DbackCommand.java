package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.katacr.katpa.KaTpaPlugin;

/** 处理 /dback 返回最近一次死亡位置指令。 */
public final class DbackCommand implements CommandExecutor {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的死亡返回指令执行器。 */
    public DbackCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 返回到最近一次死亡位置。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        plugin.dback().dback(player);
        return true;
    }
}
