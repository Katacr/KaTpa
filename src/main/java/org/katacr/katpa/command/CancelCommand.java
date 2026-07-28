package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.katacr.katpa.KaTpaPlugin;

import java.util.UUID;

/** 处理发送者主动撤销当前请求，并校验可点击文本携带的请求上下文。 */
public final class CancelCommand implements CommandExecutor {
    private final KaTpaPlugin plugin;

    /** 创建绑定请求服务的撤销指令执行器。 */
    public CancelCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 撤销当前请求；可点击文本传入 UUID 时拒绝误撤销后续的新请求。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        UUID requestId = null;
        if (args.length > 0) {
            try {
                requestId = UUID.fromString(args[0]);
            } catch (IllegalArgumentException ignored) {
                plugin.messages().send(player, "request-stale");
                return true;
            }
        }
        plugin.requests().cancel(player, requestId);
        return true;
    }
}
