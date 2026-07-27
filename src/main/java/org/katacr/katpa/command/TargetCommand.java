package org.katacr.katpa.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.RequestType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 处理 /tpa 与 /tpahere 的目标查找及无参数 Dialog 选择。 */
public final class TargetCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;
    private final RequestType type;

    /** 创建固定传送方向的目标指令执行器。 */
    public TargetCommand(KaTpaPlugin plugin, RequestType type) {
        this.plugin = plugin;
        this.type = type;
    }

    /** 执行玩家目标选择或直接发起请求。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.interactions().showPlayerSelector(player, type);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(player, "player-not-found", Map.of("player", args[0]));
            return true;
        }
        plugin.requests().create(player, target, type);
        return true;
    }

    /** 补全除发送者本人外的在线玩家名称。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !(sender instanceof Player senderPlayer)
                        || !player.getUniqueId().equals(senderPlayer.getUniqueId()))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
