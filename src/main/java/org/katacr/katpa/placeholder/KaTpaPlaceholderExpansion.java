package org.katacr.katpa.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.Home;
import org.katacr.katpa.model.LocationRecord;
import org.katacr.katpa.model.Warp;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** 对外暴露玩家家、地标和死亡位置的 PAPI 占位符。 */
public final class KaTpaPlaceholderExpansion extends PlaceholderExpansion {
    private final KaTpaPlugin plugin;

    /** 创建绑定插件服务的占位符扩展。 */
    public KaTpaPlaceholderExpansion(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 占位符前缀，例如 katpa_home_1_server。 */
    @Override
    public @NotNull String getIdentifier() {
        return "katpa";
    }

    /** 占位符扩展作者。 */
    @Override
    public @NotNull String getAuthor() {
        return "katacr";
    }

    /** 占位符扩展版本。 */
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** 按占位符格式返回对应坐标值，无法解析时返回空字符串。 */
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return null;
        }
        Player player = offlinePlayer.getPlayer();
        if (player == null && !offlinePlayer.hasPlayedBefore()) {
            return null;
        }
        java.util.UUID playerId = offlinePlayer.getUniqueId();
        String lower = params.toLowerCase(java.util.Locale.ROOT);

        if (lower.startsWith("home_")) {
            return handleHome(playerId, lower.substring("home_".length()));
        }
        if (lower.startsWith("warp_")) {
            return handleWarp(lower.substring("warp_".length()));
        }
        if (lower.startsWith("back_")) {
            return handleBack(playerId, lower.substring("back_".length()));
        }
        if (lower.startsWith("dback_")) {
            return handleDback(playerId, lower.substring("dback_".length()));
        }
        if (lower.equals("mode")) {
            return plugin.messages().text(plugin.settings().mode(playerId).languageKey());
        }
        return null;
    }

    /** 解析 %katpa_home_<n>[_字段]% 占位符。 */
    private String handleHome(java.util.UUID playerId, String rest) {
        List<Home> homes = plugin.homeStore().all(playerId).stream()
                .sorted(Comparator.comparingLong(Home::createdAt))
                .collect(Collectors.toList());
        return handleIndexed(rest, homes.size(), index -> {
            Home home = homes.get(index - 1);
            return new FieldValue(home.server(), home.world(), home.x(), home.y(), home.z(),
                    home.yaw(), home.pitch());
        });
    }

    /** 解析 %katpa_warp_<n>[_字段]% 占位符。 */
    private String handleWarp(String rest) {
        List<Warp> warps = plugin.warpStore().all().stream()
                .sorted(Comparator.comparingLong(Warp::createdAt))
                .collect(Collectors.toList());
        return handleIndexed(rest, warps.size(), index -> {
            Warp warp = warps.get(index - 1);
            return new FieldValue(warp.server(), warp.world(), warp.x(), warp.y(), warp.z(),
                    warp.yaw(), warp.pitch());
        });
    }

    /** 解析 %katpa_back_[字段]% 占位符（上次传送位置，单条）。 */
    private String handleBack(java.util.UUID playerId, String rest) {
        LocationRecord record = plugin.backStore().lastLocation(playerId);
        if (record == null) {
            return "";
        }
        FieldValue value = new FieldValue(record.server(), record.world(), record.x(), record.y(),
                record.z(), record.yaw(), record.pitch());
        if (rest.isEmpty()) {
            return String.join(",", value.server, value.world,
                    format(value.x), format(value.y), format(value.z),
                    format(value.yaw), format(value.pitch));
        }
        return switch (rest) {
            case "location" -> String.join(",", value.world,
                    format(value.x), format(value.y), format(value.z));
            case "server" -> value.server;
            case "world" -> value.world;
            case "x" -> format(value.x);
            case "y" -> format(value.y);
            case "z" -> format(value.z);
            case "yaw" -> format(value.yaw);
            case "pitch" -> format(value.pitch);
            default -> null;
        };
    }

    /** 解析 %katpa_dback[_字段]% 占位符（只保留最近一次死亡位置）。 */
    private String handleDback(java.util.UUID playerId, String rest) {
        LocationRecord record = plugin.backStore().deathLocation(playerId);
        if (record == null) {
            return "";
        }
        FieldValue value = new FieldValue(record.server(), record.world(), record.x(), record.y(),
                record.z(), record.yaw(), record.pitch());
        if (rest == null || rest.isEmpty()) {
            return String.join(",", value.server, value.world,
                    format(value.x), format(value.y), format(value.z),
                    format(value.yaw), format(value.pitch));
        }
        String field = rest.startsWith("_") ? rest.substring(1) : rest;
        return switch (field) {
            case "location" -> String.join(",", value.world,
                    format(value.x), format(value.y), format(value.z));
            case "server" -> value.server;
            case "world" -> value.world;
            case "x" -> format(value.x);
            case "y" -> format(value.y);
            case "z" -> format(value.z);
            case "yaw" -> format(value.yaw);
            case "pitch" -> format(value.pitch);
            default -> null;
        };
    }

    /** 通用索引占位符解析：<n> 或 <n>_<字段>。 */
    private String handleIndexed(String rest, int total, FieldResolver resolver) {
        String[] parts = rest.split("_", 2);
        int index;
        try {
            index = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (index < 1 || index > total) {
            return "";
        }
        FieldValue value = resolver.resolve(index);
        if (parts.length < 2 || parts[1].isEmpty()) {
            return String.join(",", value.server, value.world,
                    format(value.x), format(value.y), format(value.z),
                    format(value.yaw), format(value.pitch));
        }
        return switch (parts[1]) {
            case "location" -> String.join(",", value.world,
                    format(value.x), format(value.y), format(value.z));
            case "server" -> value.server;
            case "world" -> value.world;
            case "x" -> format(value.x);
            case "y" -> format(value.y);
            case "z" -> format(value.z);
            case "yaw" -> format(value.yaw);
            case "pitch" -> format(value.pitch);
            default -> null;
        };
    }

    /** 保留两位小数的坐标格式化，整数不显示小数部分。 */
    private static String format(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return String.format("%.2f", value);
    }

    /** 单个位置的全部坐标字段。 */
    private record FieldValue(String server, String world, double x, double y, double z,
                              float yaw, float pitch) {
    }

    /** 按索引解析位置字段值的策略。 */
    @FunctionalInterface
    private interface FieldResolver {
        /** 返回第 index 个位置（从 1 开始）的全部字段。 */
        FieldValue resolve(int index);
    }
}
