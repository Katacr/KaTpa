package org.katacr.katpa.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 处理 /katap help 与 /katap reload 主命令。 */
public final class KaTpaCommand implements CommandExecutor, TabCompleter {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件配置和语言服务的主命令。 */
    public KaTpaCommand(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 显示帮助，或在权限允许时重载配置和语言文件。 */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("katpa.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            plugin.network().shutdown();
            plugin.reloadConfig();
            plugin.messages().reload();
            plugin.network().initialize();
            plugin.messages().send(sender, "config-reloaded");
            return true;
        }
        if (args[0].equalsIgnoreCase("warp")) {
            handleWarp(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("pwarp")) {
            handlePlayerWarp(sender, args);
            return true;
        }
        sendHelp(sender);
        return true;
    }

    /** 处理 /katpa warp <子命令>：edit/create/delete/set/icon/rename 地标管理操作。 */
    private void handleWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "warp-admin-usage");
            return;
        }
        if (!player.hasPermission("katpa.warp.admin")) {
            plugin.messages().send(player, "warp-no-edit-permission");
            return;
        }
        String sub = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "edit" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "warp-edit-usage");
                    return;
                }
                String name = args[2];
                org.katacr.katpa.model.Warp warp = plugin.warpStore().find(name);
                if (warp == null) {
                    plugin.messages().send(player, "warp-not-found", Map.of("name", name));
                    return;
                }
                plugin.interactions().showWarpEditor(player, warp);
            }
            case "create" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "warp-create-usage");
                    return;
                }
                player.performCommand("setwarp " + args[2]);
                plugin.interactions().showWarpManager(player);
            }
            case "delete" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "warp-delete-usage");
                    return;
                }
                player.performCommand("delwarp " + args[2]);
                plugin.interactions().showWarpManager(player);
            }
            case "rename" -> {
                if (args.length < 4) {
                    plugin.messages().send(player, "warp-rename-usage");
                    return;
                }
                if (plugin.warp().rename(args[2], args[3])) {
                    plugin.messages().send(player, "warp-renamed",
                            Map.of("old", args[2], "new", args[3]));
                } else {
                    plugin.messages().send(player, "warp-rename-failed");
                }
                plugin.interactions().showWarpManager(player);
            }
            case "icon" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "warp-seticon-usage");
                    return;
                }
                org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == org.bukkit.Material.AIR) {
                    plugin.messages().send(player, "warp-seticon-empty-hand");
                    return;
                }
                plugin.warp().setIcon(args[2], hand);
                plugin.messages().send(player, "warp-icon-updated", Map.of("name", args[2]));
                plugin.interactions().showWarpEditor(player, plugin.warpStore().find(args[2]));
            }
            case "set" -> {
                handleWarpSet(player, args);
            }
            default -> plugin.messages().send(player, "warp-admin-usage");
        }
    }

    /** 处理 /katpa warp set <字段> <名称> [值]。 */
    private void handleWarpSet(org.bukkit.entity.Player player, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(player, "warp-set-usage");
            return;
        }
        String field = args[2].toLowerCase(java.util.Locale.ROOT);
        String name = args[3];
        switch (field) {
            case "name" -> {
                if (args.length < 5) {
                    plugin.messages().send(player, "warp-rename-usage");
                    return;
                }
                if (plugin.warp().rename(name, args[4])) {
                    plugin.messages().send(player, "warp-renamed", Map.of("old", name, "new", args[4]));
                } else {
                    plugin.messages().send(player, "warp-rename-failed");
                }
            }
            case "permission" -> {
                String value = args.length >= 5 ? args[4] : "";
                plugin.warp().setPermission(name, value);
                plugin.messages().send(player, "warp-permission-updated",
                        Map.of("name", name, "permission", value));
            }
            case "cooldown" -> {
                if (args.length < 5) {
                    plugin.messages().send(player, "warp-set-usage");
                    return;
                }
                try {
                    plugin.warp().setCooldown(name, Integer.parseInt(args[4].trim()));
                    plugin.messages().send(player, "warp-cooldown-updated",
                            Map.of("name", name, "seconds", args[4]));
                } catch (NumberFormatException e) {
                    plugin.messages().send(player, "invalid-number");
                }
            }
            case "cost" -> {
                if (args.length < 5) {
                    plugin.messages().send(player, "warp-set-usage");
                    return;
                }
                try {
                    plugin.warp().setCost(name, Double.parseDouble(args[4].trim()));
                    plugin.messages().send(player, "warp-cost-updated",
                            Map.of("name", name, "cost", args[4]));
                } catch (NumberFormatException e) {
                    plugin.messages().send(player, "invalid-number");
                }
            }
            case "desc", "description" -> {
                if (args.length < 5) {
                    plugin.messages().send(player, "warp-set-usage");
                    return;
                }
                String desc = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                plugin.warp().setDescription(name, desc);
                plugin.messages().send(player, "warp-desc-updated", Map.of("name", name));
            }
            default -> plugin.messages().send(player, "warp-set-usage");
        }
    }

    /** 处理 /katpa pwarp <子命令>：edit/create/delete/rename/icon/set/rate 玩家地标管理操作。 */
    private void handlePlayerWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "pwarp-admin-usage");
            return;
        }
        String sub = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "edit" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "pwarp-edit-usage");
                    return;
                }
                org.katacr.katpa.model.PlayerWarp warp = plugin.playerWarpStore().find(args[2]);
                if (warp == null) {
                    plugin.messages().send(player, "pwarp-not-found", Map.of("name", args[2]));
                    return;
                }
                if (!warp.ownerId().equals(player.getUniqueId()) && !player.hasPermission("katpa.pwarp.admin")) {
                    plugin.messages().send(player, "pwarp-no-edit-permission");
                    return;
                }
                plugin.interactions().showPwarpEditor(player, warp);
            }
            case "create" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "pwarp-create-usage");
                    return;
                }
                plugin.playerWarp().create(player, args[2]);
                plugin.interactions().showPwarpManager(player);
            }
            case "delete" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "pwarp-delete-usage");
                    return;
                }
                plugin.playerWarp().delete(player, args[2]);
                plugin.interactions().showPwarpManager(player);
            }
            case "rename" -> {
                if (args.length < 4) {
                    plugin.messages().send(player, "pwarp-rename-usage");
                    return;
                }
                plugin.playerWarp().rename(player, args[2], args[3]);
                plugin.interactions().showPwarpManager(player);
            }
            case "icon" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "pwarp-seticon-usage");
                    return;
                }
                org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == org.bukkit.Material.AIR) {
                    plugin.messages().send(player, "pwarp-seticon-empty-hand");
                    return;
                }
                plugin.playerWarp().setIcon(player, args[2], hand);
                plugin.interactions().showPwarpEditor(player, plugin.playerWarpStore().find(args[2]));
            }
            case "rate" -> {
                if (args.length < 4) {
                    plugin.messages().send(player, "pwarp-rate-usage");
                    return;
                }
                try {
                    int stars = Integer.parseInt(args[3]);
                    plugin.playerWarp().rate(player, args[2], stars);
                } catch (NumberFormatException e) {
                    plugin.messages().send(player, "pwarp-rate-invalid");
                }
            }
            case "set" -> handlePlayerWarpSet(player, args);
            default -> plugin.messages().send(player, "pwarp-admin-usage");
        }
    }

    /** 处理 /katpa pwarp set <字段> <名称> [值]。 */
    private void handlePlayerWarpSet(org.bukkit.entity.Player player, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(player, "pwarp-set-usage");
            return;
        }
        String field = args[2].toLowerCase(java.util.Locale.ROOT);
        String name = args[3];
        switch (field) {
            case "name" -> {
                if (args.length < 5) {
                    plugin.messages().send(player, "pwarp-rename-usage");
                    return;
                }
                plugin.playerWarp().rename(player, name, args[4]);
                plugin.interactions().showPwarpManager(player);
            }
            case "cost" -> {
                if (args.length < 5) {
                    plugin.messages().send(player, "pwarp-set-usage");
                    return;
                }
                try {
                    plugin.playerWarp().setCost(player, name, Double.parseDouble(args[4].trim()));
                } catch (NumberFormatException e) {
                    plugin.messages().send(player, "invalid-number");
                }
            }
            case "cooldown" -> {
                if (args.length < 5) {
                    plugin.messages().send(player, "pwarp-set-usage");
                    return;
                }
                try {
                    plugin.playerWarp().setCooldown(player, name, Integer.parseInt(args[4].trim()));
                } catch (NumberFormatException e) {
                    plugin.messages().send(player, "invalid-number");
                }
            }
            case "desc", "description" -> {
                if (args.length < 5) {
                    plugin.messages().send(player, "pwarp-set-usage");
                    return;
                }
                String desc = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                plugin.playerWarp().setDescription(player, name, desc);
                plugin.interactions().showPwarpEditor(player, plugin.playerWarpStore().find(name));
            }
            default -> plugin.messages().send(player, "pwarp-set-usage");
        }
    }

    /** 补全主命令的 help、reload、warp 子命令。 */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("help", "reload", "warp", "pwarp").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("warp")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return List.of("edit", "create", "delete", "rename", "icon", "set").stream()
                        .filter(v -> v.startsWith(prefix))
                        .toList();
            }
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3 && (sub.equals("edit") || sub.equals("delete")
                    || sub.equals("rename") || sub.equals("icon")) && plugin.warpStore() != null) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.warpStore().all().stream()
                        .map(org.katacr.katpa.model.Warp::name)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            if (args.length == 4 && sub.equals("set")) {
                String prefix = args[3].toLowerCase(Locale.ROOT);
                return List.of("name", "permission", "cooldown", "cost", "desc").stream()
                        .filter(v -> v.startsWith(prefix))
                        .toList();
            }
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("pwarp") && plugin.playerWarpStore() != null) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return List.of("edit", "create", "delete", "rename", "icon", "set", "rate").stream()
                        .filter(v -> v.startsWith(prefix))
                        .toList();
            }
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3 && (sub.equals("edit") || sub.equals("delete")
                    || sub.equals("rename") || sub.equals("icon") || sub.equals("rate"))) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.playerWarpStore().all().stream()
                        .map(org.katacr.katpa.model.PlayerWarp::name)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            if (args.length == 4 && sub.equals("rate")) {
                return List.of("1", "2", "3", "4", "5");
            }
            if (args.length == 4 && sub.equals("set")) {
                String prefix = args[3].toLowerCase(Locale.ROOT);
                return List.of("name", "cost", "cooldown", "desc").stream()
                        .filter(v -> v.startsWith(prefix))
                        .toList();
            }
            if (args.length == 3 && sub.equals("set")) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.playerWarpStore().all().stream()
                        .map(org.katacr.katpa.model.PlayerWarp::name)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
        }
        return List.of();
    }

    /** 从语言文件逐行发送已启用模块的玩家指令帮助。 */
    private void sendHelp(CommandSender sender) {
        plugin.messages().sendComponent(sender,
                plugin.messages().component("help.header", Map.of(), false));
        List<String> keys = new java.util.ArrayList<>(List.of("reload"));
        if (plugin.moduleEnabled("tpa")) {
            keys.addAll(0, List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "settings"));
        }
        if (plugin.moduleEnabled("back")) {
            keys.add(0, "back");
        }
        if (plugin.moduleEnabled("dback")) {
            keys.add(0, "dback");
        }
        if (plugin.moduleEnabled("warp")) {
            keys.add(0, "warp");
            keys.add(0, "setwarp");
            keys.add(0, "warpedit");
        }
        if (plugin.moduleEnabled("home")) {
            keys.add(0, "home");
            keys.add(0, "sethome");
        }
        if (plugin.moduleEnabled("pwarp")) {
            keys.add(0, "pwarp");
            keys.add(0, "pwarpedit");
        }
        for (String key : keys) {
            plugin.messages().sendComponent(sender,
                    plugin.messages().component("help." + key, Map.of(), false));
        }
    }
}
