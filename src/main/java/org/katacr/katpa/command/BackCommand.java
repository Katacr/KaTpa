package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.katacr.katpa.KaTpaPlugin;

/** 处理 /back 返回上次位置指令。 */
public final class BackCommand implements CommandExecutor {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的返回指令执行器。 */
    public BackCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 玩家专属，直接调用返回服务。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        plugin.back().back(player);
        return true;
    }
}
