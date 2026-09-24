package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.PlayerWarp;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 处理 /pwarp 玩家地标指令。
 *
 * <p>无参数打开玩家地标排行榜；二级子命令直接打开对应界面：
 * {@code leaderboard}（排行榜）、{@code favorites}（收藏）、{@code mine}（我的地标）、
 * {@code history}（传送历史）；{@code admin} 提供管理员编辑/删除/重载。
 *
 * <p>按名快捷传送请使用 {@code /pw <名称>}。
 */
public final class PlayerWarpCommand implements CommandExecutor, TabCompleter {
    /** 支持的二级子命令。 */
    private static final List<String> SUBCOMMANDS =
            List.of("leaderboard", "favorites", "mine", "history", "admin");
    /** 管理员二级子命令。 */
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("edit", "delete", "reload");

    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的玩家地标指令执行器。 */
    public PlayerWarpCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 无参数打开玩家地标排行榜；子命令打开对应界面。 */
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
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "leaderboard", "top", "rank" -> plugin.interactions().showPwarpLeaderboard(player);
            case "favorites", "favorite", "fav" -> plugin.interactions().showPwarpFavorites(player);
            case "mine", "my", "manager" -> plugin.interactions().showPwarpManager(player);
            case "history", "his" -> plugin.interactions().showPwarpHistory(player);
            case "admin" -> handleAdmin(player, args);
            default -> plugin.messages().send(player, "pwarp-command-usage");
        }
        return true;
    }

    /** 处理 /pwarp admin <edit|delete|reload>：管理员强行编辑/删除任意地标，或仅重载地标数据与排行榜缓存。 */
    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("katpa.pwarp.admin")) {
            plugin.messages().send(player, "pwarp-no-edit-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "pwarp-admin-command-usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "edit" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "pwarp-admin-edit-usage");
                    return;
                }
                PlayerWarp warp = plugin.playerWarpStore().find(args[2]);
                if (warp == null) {
                    plugin.messages().send(player, "pwarp-not-found", Map.of("name", args[2]));
                    return;
                }
                plugin.interactions().showPwarpEditor(player, warp);
            }
            case "delete" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "pwarp-admin-delete-usage");
                    return;
                }
                PlayerWarp warp = plugin.playerWarpStore().find(args[2]);
                if (warp == null) {
                    plugin.messages().send(player, "pwarp-not-found", Map.of("name", args[2]));
                    return;
                }
                        plugin.playerWarpStore().remove(warp.ownerId(), warp.name());
                plugin.messages().send(player, "pwarp-deleted", Map.of("name", warp.name()));
            }
            case "reload" -> {
                plugin.playerWarp().reloadData();
                plugin.messages().send(player, "pwarp-admin-reloaded");
            }
            default -> plugin.messages().send(player, "pwarp-admin-command-usage");
        }
    }

    /** 补全固定子命令与管理员子命令；不检索玩家地标（避免频繁读库）。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (!args[0].equalsIgnoreCase("admin")) {
            return List.of();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return ADMIN_SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("delete"))
                && plugin.playerWarpStore() != null) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return plugin.playerWarpStore().all().stream()
                    .map(PlayerWarp::name)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
