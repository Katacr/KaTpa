package org.katacr.katpa.ui.inventory.gui;

import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.ListType;

/**
 * KaTpa 业务动作处理器，处理 {@code katpa: <动作>} 形式的菜单动作。
 *
 * 通过 {@link GuiManager#registerActionHandler(String, GuiActionHandler)} 注册，
 * 支持设置接受模式、名单管理、地标与家的创建/编辑/删除、传送请求发送等。
 * 纯文本动作，不含平台专属菜单依赖。
 */
public final class KaTpaGuiActions implements GuiActionHandler {
    private final KaTpaPlugin plugin;
    private final ChatInputManager chat;
    private final GuiManager gui;

    /** 创建绑定插件实例的业务动作处理器。 */
    public KaTpaGuiActions(KaTpaPlugin plugin, ChatInputManager chat, GuiManager gui) {
        this.plugin = plugin;
        this.chat = chat;
        this.gui = gui;
    }

    @Override
    public void execute(Player player, String namespace, String payload, MenuSession session) {
        if (payload == null) {
            payload = "";
        }
        String rest = payload.trim();
        switch (namespace.toLowerCase()) {
            case "setting" -> handleSetting(player, rest, session);
            case "relation" -> handleRelation(player, rest, session);
            case "warp" -> handleWarp(player, rest, session);
            case "pwarp" -> handlePlayerWarp(player, rest, session);
            case "home" -> handleHome(player, rest, session);
            case "request" -> handleRequest(player, rest, session);
            case "page" -> handlePage(player, rest, session);
            default -> plugin.getLogger().warning("未知的 katpa 业务动作命名空间: " + namespace);
        }
    }

    /** 处理翻页动作：page prev / page next。仅在页码实际变化时重渲染，否则不做任何事。 */
    private void handlePage(Player player, String rest, MenuSession session) {
        String direction = rest.trim().toLowerCase();
        int current = session.page();
        int target = current;
        if ("prev".equals(direction)) {
            target = Math.max(0, current - 1);
        } else if ("next".equals(direction)) {
            int total = 1;
            try {
                total = Integer.parseInt(session.get("total_pages"));
            } catch (NumberFormatException ignored) {
            }
            target = Math.min(Math.max(1, total) - 1, current + 1);
        }
        if (target == current) {
            // 已到首页/末页或无法翻页：不执行任何动作，避免重复重算。
            return;
        }
        session.page(target);
        gui.reopen(player);
    }

    /** 处理设置相关动作：setting mode <chat|dialog|sneak|cycle|toggle>。 */
    private void handleSetting(Player player, String rest, MenuSession session) {
        String[] args = rest.trim().split("\\s+");
        if (args.length >= 1 && "mode".equalsIgnoreCase(args[0])) {
            String value = args.length >= 2 ? args[1] : "cycle";
            if ("cycle".equalsIgnoreCase(value)) {
                value = "toggle";
            }
            player.performCommand("tpasetting mode " + value);
        }
        plugin.interactions().showSettings(player);
    }

    /** 处理名单相关动作：relation add <type> / relation remove <type> <name>。 */
    private void handleRelation(Player player, String rest, MenuSession session) {
        String[] args = rest.split("\\s+");
        if (args.length >= 2 && "add".equalsIgnoreCase(args[0])) {
            String type = args[1];
            String listTypeDisplay = plugin.messages().text("list." + ("whitelist".equalsIgnoreCase(type) ? "whitelist" : "blacklist"));
            ListType listType = "whitelist".equalsIgnoreCase(type) ? ListType.WHITELIST : ListType.BLACKLIST;
            chat.capture(player, plugin.messages().text("gui.prompt.relation-add", java.util.Map.of("list", listTypeDisplay)), (p, name) -> {
                p.performCommand("tpasetting " + type + " add " + name);
                plugin.interactions().showRelationEditor(p, listType);
            });
        } else if (args.length >= 3 && "remove".equalsIgnoreCase(args[0])) {
            String type = args[1];
            String name = args[2];
            player.performCommand("tpasetting " + type + " remove " + name);
            ListType listType = "whitelist".equalsIgnoreCase(type) ? ListType.WHITELIST : ListType.BLACKLIST;
            plugin.interactions().showRelationEditor(player, listType);
        }
    }

    /** 处理地标相关动作：warp create / warp delete <name> / warp set <field> <name> / warp icon <name> / warp rename <name> / warp edit <name>。 */
    private void handleWarp(Player player, String rest, MenuSession session) {
        String[] args = rest.split("\\s+");
        if (args.length >= 1 && "create".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("katpa.warp.admin")) {
                plugin.messages().send(player, "warp-no-edit-permission");
                return;
            }
            chat.capture(player, plugin.messages().text("gui.prompt.warp-create"), (p, name) -> {
                p.performCommand("setwarp " + name);
                plugin.interactions().showWarpManager(p);
            });
        } else if (args.length >= 2 && "edit".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("katpa.warp.admin")) {
                plugin.messages().send(player, "warp-no-edit-permission");
                return;
            }
            plugin.interactions().showWarpEditor(player, plugin.warpStore().find(args[1]));
        } else if (args.length >= 2 && "delete".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("katpa.warp.admin")) {
                plugin.messages().send(player, "warp-no-edit-permission");
                return;
            }
            String name = args[1];
            player.performCommand("delwarp " + name);
            plugin.interactions().showWarpManager(player);
        } else if (args.length >= 3 && "set".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("katpa.warp.admin")) {
                plugin.messages().send(player, "warp-no-edit-permission");
                return;
            }
            String field = args[1].toLowerCase();
            String name = args[2];
            switch (field) {
                case "name" -> chat.capture(player, plugin.messages().text("gui.prompt.warp-rename"), (p, newName) -> {
                    if (plugin.warp().rename(name, newName)) {
                        plugin.messages().send(p, "warp-renamed", java.util.Map.of("old", name, "new", newName));
                        plugin.interactions().showWarpEditor(p, plugin.warpStore().find(newName));
                    } else {
                        plugin.messages().send(p, "warp-rename-failed");
                        plugin.interactions().showWarpEditor(p, plugin.warpStore().find(name));
                    }
                });
                case "permission" -> chat.capture(player, plugin.messages().text("gui.prompt.warp-permission"), (p, value) -> {
                    plugin.warp().setPermission(name, value);
                    plugin.interactions().showWarpEditor(p, plugin.warpStore().find(name));
                });
                case "cooldown" -> chat.capture(player, plugin.messages().text("gui.prompt.warp-cooldown"), (p, value) -> {
                    try {
                        plugin.warp().setCooldown(name, Integer.parseInt(value.trim()));
                    } catch (NumberFormatException e) {
                        plugin.messages().send(p, "invalid-number");
                    }
                    plugin.interactions().showWarpEditor(p, plugin.warpStore().find(name));
                });
                case "cost" -> chat.capture(player, plugin.messages().text("gui.prompt.warp-cost"), (p, value) -> {
                    try {
                        plugin.warp().setCost(name, Double.parseDouble(value.trim()));
                    } catch (NumberFormatException e) {
                        plugin.messages().send(p, "invalid-number");
                    }
                    plugin.interactions().showWarpEditor(p, plugin.warpStore().find(name));
                });
                case "desc", "description" -> chat.capture(player, plugin.messages().text("gui.prompt.warp-desc"), (p, value) -> {
                    plugin.warp().setDescription(name, value);
                    plugin.interactions().showWarpEditor(p, plugin.warpStore().find(name));
                });
                default -> plugin.getLogger().warning("未知的 warp set 字段: " + field);
            }
        } else if (args.length >= 2 && "icon".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("katpa.warp.admin")) {
                plugin.messages().send(player, "warp-no-edit-permission");
                return;
            }
            String name = args[1];
            if (plugin.warpStore().find(name) == null) {
                plugin.messages().send(player, "warp-not-found", java.util.Map.of("name", name));
                return;
            }
            startIconSetup(player, IconTarget.WARP, name);
        }
    }

    /** 处理玩家地标相关动作：pwarp edit/delete/set/icon/rate/do rate <name> [stars]。 */
    private void handlePlayerWarp(Player player, String rest, MenuSession session) {
        String[] args = rest.split("\\s+");
        if (args.length >= 2 && "warp".equalsIgnoreCase(args[0])) {
            plugin.playerWarp().warp(player, args[1]);
        } else if (args.length >= 1 && "create".equalsIgnoreCase(args[0])) {
            chat.capture(player, plugin.messages().text("gui.prompt.pwarp-create"), (p, name) -> {
                plugin.playerWarp().create(p, name);
                plugin.interactions().showPwarpManager(p);
            });
        } else if (args.length >= 2 && "edit".equalsIgnoreCase(args[0])) {
            org.katacr.katpa.model.PlayerWarp warp = plugin.playerWarpStore().find(args[1]);
            if (warp == null) {
                return;
            }
            if (!warp.ownerId().equals(player.getUniqueId()) && !player.hasPermission("katpa.pwarp.admin")) {
                plugin.messages().send(player, "pwarp-no-edit-permission");
                return;
            }
            plugin.interactions().showPwarpEditor(player, warp);
        } else if (args.length >= 2 && "delete".equalsIgnoreCase(args[0])) {
            plugin.playerWarp().delete(player, args[1]);
            plugin.interactions().showPwarpManager(player);
        } else if (args.length >= 3 && "set".equalsIgnoreCase(args[0])) {
            handlePlayerWarpSet(player, args);
        } else if (args.length >= 2 && "update".equalsIgnoreCase(args[0])) {
            if (plugin.playerWarp().updateLocation(player, args[1])) {
                plugin.interactions().showPwarpEditor(player, plugin.playerWarpStore().find(args[1]));
            }
        } else if (args.length >= 2 && "icon".equalsIgnoreCase(args[0])) {
            org.katacr.katpa.model.PlayerWarp warp = plugin.playerWarpStore().find(args[1]);
            if (warp == null) {
                plugin.messages().send(player, "pwarp-not-found", java.util.Map.of("name", args[1]));
                return;
            }
            if (!warp.ownerId().equals(player.getUniqueId()) && !player.hasPermission("katpa.pwarp.admin")) {
                plugin.messages().send(player, "pwarp-no-edit-permission");
                return;
            }
            startIconSetup(player, IconTarget.PWARP, args[1]);
        } else if (args.length >= 2 && "rate".equalsIgnoreCase(args[0])) {
            org.katacr.katpa.model.PlayerWarp warp = plugin.playerWarpStore().find(args[1]);
            if (warp == null) {
                return;
            }
            if (warp.ownerId().equals(player.getUniqueId())) {
                plugin.messages().send(player, "pwarp-rate-own");
                return;
            }
            plugin.interactions().showPwarpRate(player, warp);
        } else if (args.length >= 2 && "favorite".equalsIgnoreCase(args[0])) {
            plugin.playerWarp().toggleFavorite(player, args[1]);
            gui.reopen(player);
        } else if (args.length >= 2 && "select".equalsIgnoreCase(args[0])) {
            session.set("pwarp_rate_selected", args[1]);
            gui.reopen(player);
        } else if (args.length >= 4 && "do".equalsIgnoreCase(args[0]) && "rate".equalsIgnoreCase(args[1])) {
            try {
                int stars = Integer.parseInt(args[3]);
                plugin.playerWarp().rate(player, args[2], stars);
            } catch (NumberFormatException e) {
                plugin.messages().send(player, "pwarp-rate-invalid");
            }
        }
    }

    /** 处理玩家地标 set 子动作：name/cost/desc。 */
    private void handlePlayerWarpSet(Player player, String[] args) {
        String field = args[1].toLowerCase();
        String name = args[2];
        switch (field) {
            case "name" -> chat.capture(player, plugin.messages().text("gui.prompt.pwarp-rename"), (p, newName) -> {
                if (plugin.playerWarp().rename(p, name, newName)) {
                    plugin.interactions().showPwarpEditor(p, plugin.playerWarpStore().find(newName));
                } else {
                    plugin.interactions().showPwarpEditor(p, plugin.playerWarpStore().find(name));
                }
            });
            case "cost" -> chat.capture(player, plugin.messages().text("gui.prompt.pwarp-cost"), (p, value) -> {
                try {
                    plugin.playerWarp().setCost(p, name, Double.parseDouble(value.trim()));
                } catch (NumberFormatException e) {
                    plugin.messages().send(p, "invalid-number");
                }
                plugin.interactions().showPwarpEditor(p, plugin.playerWarpStore().find(name));
            });
            case "desc", "description" -> chat.capture(player, plugin.messages().text("gui.prompt.pwarp-desc"), (p, value) -> {
                plugin.playerWarp().setDescription(p, name, value);
                plugin.interactions().showPwarpEditor(p, plugin.playerWarpStore().find(name));
            });
            default -> plugin.getLogger().warning("未知的 pwarp set 字段: " + field);
        }
    }

    /** 处理家相关动作：home create / home edit <name> / home delete <name> / home update <name> / home set <field> <name> / home icon <name> / home rename <name>。 */
    private void handleHome(Player player, String rest, MenuSession session) {
        String[] args = rest.split("\\s+");
        if (args.length >= 1 && "create".equalsIgnoreCase(args[0])) {
            chat.capture(player, plugin.messages().text("gui.prompt.home-create"), (p, name) -> {
                p.performCommand("sethome " + name);
                plugin.interactions().showHomeManager(p);
            });
        } else if (args.length >= 2 && "edit".equalsIgnoreCase(args[0])) {
            String name = args[1];
            org.katacr.katpa.model.Home home = plugin.homeStore().find(player.getUniqueId(), name);
            if (home == null) {
                plugin.messages().send(player, "home-not-found", java.util.Map.of("name", name));
                return;
            }
            plugin.interactions().showHomeEditor(player, home);
        } else if (args.length >= 2 && "delete".equalsIgnoreCase(args[0])) {
            String name = args[1];
            player.performCommand("delhome " + name);
            plugin.interactions().showHomeManager(player);
        } else if (args.length >= 2 && "update".equalsIgnoreCase(args[0])) {
            String name = args[1];
            if (plugin.home().updateLocation(player, name)) {
                plugin.interactions().showHomeEditor(player, plugin.homeStore().find(player.getUniqueId(), name));
            }
        } else if (args.length >= 3 && "set".equalsIgnoreCase(args[0])) {
            String field = args[1].toLowerCase();
            String name = args[2];
            java.util.UUID ownerId = player.getUniqueId();
            switch (field) {
                case "name" -> chat.capture(player, plugin.messages().text("gui.prompt.home-rename"), (p, newName) -> {
                    if (plugin.home().rename(ownerId, name, newName)) {
                        plugin.messages().send(p, "home-renamed", java.util.Map.of("old", name, "new", newName));
                        plugin.interactions().showHomeEditor(p,
                                plugin.homeStore().find(ownerId, newName));
                    } else {
                        plugin.messages().send(p, "home-rename-failed");
                        plugin.interactions().showHomeEditor(p, plugin.homeStore().find(ownerId, name));
                    }
                });
                case "desc", "description" -> chat.capture(player, plugin.messages().text("gui.prompt.home-desc"), (p, value) -> {
                    plugin.home().setDescription(ownerId, name, value);
                    plugin.interactions().showHomeManager(p);
                });
                default -> plugin.getLogger().warning("未知的 home set 字段: " + field);
            }
        } else if (args.length >= 2 && "icon".equalsIgnoreCase(args[0])) {
            String name = args[1];
            if (plugin.homeStore().find(player.getUniqueId(), name) == null) {
                plugin.messages().send(player, "home-not-found", java.util.Map.of("name", name));
                return;
            }
            startIconSetup(player, IconTarget.HOME, name);
        }
    }

    /** 图标设置目标类型。 */
    private enum IconTarget {
        WARP, PWARP, HOME
    }

    /** 记录玩家待设置图标的目标（点击可点击文本后据此完成设置）。 */
    private final java.util.Map<java.util.UUID, PendingIcon> pendingIcons = new java.util.concurrent.ConcurrentHashMap<>();

    private record PendingIcon(IconTarget target, String name, long expiresAt) {
    }

    /**
     * 开始图标设置：关闭容器并发送可点击文本，玩家手持物品点击 [此处] 后完成设置并回到编辑器。
     *
     * 使用 Adventure {@code ClickEvent.callback}（Paper 内置自定义回调），不会触发客户端指令二次确认。
     */
    private void startIconSetup(Player player, IconTarget target, String name) {
        pendingIcons.put(player.getUniqueId(),
                new PendingIcon(target, name, System.currentTimeMillis() + 60_000L));
        player.closeInventory();
        String promptKey = switch (target) {
            case WARP -> "warp-icon-prompt";
            case PWARP -> "pwarp-icon-prompt";
            case HOME -> "home-icon-prompt";
        };
        String text = plugin.messages().text(promptKey);
        boolean clickable = org.katacr.katpa.text.ClickableText.sendClickable(
                player, text, p -> completeIconSetup(p), java.time.Duration.ofSeconds(60));
        if (!clickable) {
            // 核心不支持自定义回调（旧 Spigot）：提示改用指令设置。
            pendingIcons.remove(player.getUniqueId());
            plugin.messages().send(player, "warp-seticon-usage");
        }
    }

    /** 完成图标设置：读取手持物品写入图标并重新打开对应编辑器。 */
    private void completeIconSetup(Player player) {
        PendingIcon pending = pendingIcons.remove(player.getUniqueId());
        if (pending == null || System.currentTimeMillis() > pending.expiresAt()) {
            plugin.messages().send(player, "warp-seticon-empty-hand");
            return;
        }
        org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == org.bukkit.Material.AIR) {
            String key = switch (pending.target()) {
                case WARP -> "warp-seticon-empty-hand";
                case PWARP -> "pwarp-seticon-empty-hand";
                case HOME -> "home-seticon-empty-hand";
            };
            plugin.messages().send(player, key);
            reopenEditor(player, pending.target(), pending.name());
            return;
        }
        switch (pending.target()) {
            case WARP -> {
                plugin.warp().setIcon(pending.name(), hand);
                plugin.messages().send(player, "warp-icon-updated", java.util.Map.of("name", pending.name()));
            }
            case PWARP -> {
                plugin.playerWarp().setIcon(player, pending.name(), hand);
                plugin.messages().send(player, "pwarp-icon-updated", java.util.Map.of("name", pending.name()));
            }
            case HOME -> {
                plugin.home().setIcon(player.getUniqueId(), pending.name(), hand);
                plugin.messages().send(player, "home-icon-updated", java.util.Map.of("name", pending.name()));
            }
        }
        reopenEditor(player, pending.target(), pending.name());
    }

    /** 图标设置完成后重新打开对应编辑器。 */
    private void reopenEditor(Player player, IconTarget target, String name) {
        switch (target) {
            case WARP -> plugin.interactions().showWarpEditor(player, plugin.warpStore().find(name));
            case PWARP -> plugin.interactions().showPwarpEditor(player, plugin.playerWarpStore().find(name));
            case HOME -> plugin.interactions().showHomeEditor(player,
                    plugin.homeStore().find(player.getUniqueId(), name));
        }
    }

    /** 处理传送请求发送：request send <player>。 */
    private void handleRequest(Player player, String rest, MenuSession session) {
        String[] args = rest.split("\\s+");
        if (args.length >= 2 && "send".equalsIgnoreCase(args[0])) {
            player.performCommand("tpa " + args[1]);
        }
    }
}
