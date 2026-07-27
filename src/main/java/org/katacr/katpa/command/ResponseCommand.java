package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.katacr.katpa.KaTpaPlugin;

import java.util.UUID;

/** 处理 /tpaccept、兼容别名 /tpaaccept 以及 /tpdeny。 */
public final class ResponseCommand implements CommandExecutor {
    private final KaTpaPlugin plugin;
    private final boolean accept;

    /** 创建固定为同意或拒绝行为的响应指令。 */
    public ResponseCommand(KaTpaPlugin plugin, boolean accept) {
        this.plugin = plugin;
        this.accept = accept;
    }

    /** 处理当前请求，并支持聊天按钮携带的请求 ID 校验。 */
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
        if (accept) {
            plugin.requests().accept(player, requestId);
        } else {
            plugin.requests().deny(player, requestId);
        }
        return true;
    }
}
