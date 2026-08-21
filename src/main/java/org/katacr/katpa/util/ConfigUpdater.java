package org.katacr.katpa.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 启动时检查配置文件版本，自动备份旧版并合并用户自定义值到新默认配置。 */
public final class ConfigUpdater {
    private static final int CURRENT_CONFIG_VERSION = 1;
    private static final String CONFIG_VERSION_KEY = "config-version";

    private ConfigUpdater() {
    }

    /** 检查并更新配置文件，返回是否执行了更新。 */
    public static boolean checkAndUpdateConfig(JavaPlugin plugin, File configFile) {
        if (!configFile.isFile()) {
            return false;
        }
        Map<String, Object> userValues = extractUserConfigValues(configFile);
        int configVersion = parseInt(userValues.get(CONFIG_VERSION_KEY), 0);
        if (configVersion >= CURRENT_CONFIG_VERSION) {
            return false;
        }
        plugin.getLogger().info("检测到旧版配置文件 (v" + configVersion + ")，正在更新到 v" + CURRENT_CONFIG_VERSION + "……");
        backupConfig(plugin, configFile, configVersion);
        copyDefaultConfig(plugin, configFile);
        writeUserValues(plugin, configFile, userValues);
        plugin.getLogger().info("配置文件更新完成。旧版本: v" + configVersion + " -> 新版本: v" + CURRENT_CONFIG_VERSION);
        return true;
    }

    /** 从用户配置文件中提取全部叶子节点值。 */
    private static Map<String, Object> extractUserConfigValues(File configFile) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        Map<String, Object> values = new HashMap<>();
        collectValues(config, "", values);
        return values;
    }

    /** 递归收集配置树中的叶子节点。 */
    private static void collectValues(ConfigurationSection section, String prefix, Map<String, Object> values) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(path);
            if (value instanceof ConfigurationSection sub) {
                collectValues(sub, path, values);
            } else if (value instanceof List<?> || value instanceof Number || value instanceof String
                    || value instanceof Boolean || value instanceof Character) {
                values.put(path, value);
            }
        }
    }

    /** 创建带版本号和时间戳的备份文件。 */
    private static void backupConfig(JavaPlugin plugin, File configFile, int configVersion) {
        String timestamp = Long.toString(System.currentTimeMillis());
        String fileName = configFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex + 1) : "yml";
        String backupName = baseName + "_v" + configVersion + "_backup_" + timestamp + "." + extension;
        File backupFile = new File(configFile.getParentFile(), backupName);
        try {
            Files.copy(configFile.toPath(), backupFile.toPath());
            plugin.getLogger().info("旧配置已备份至: " + backupName);
        } catch (Exception e) {
            plugin.getLogger().warning("备份旧配置失败: " + e.getMessage());
        }
    }

    /** 删除旧配置并从 JAR 中提取新默认配置。 */
    private static void copyDefaultConfig(JavaPlugin plugin, File configFile) {
        try {
            Files.delete(configFile.toPath());
        } catch (Exception e) {
            plugin.getLogger().warning("删除旧配置文件失败: " + e.getMessage());
        }
        plugin.saveResource("config.yml", false);
    }

    /** 将用户自定义值写回新默认配置，跳过 config-version 和不存在于新配置的键。 */
    private static void writeUserValues(JavaPlugin plugin, File configFile, Map<String, Object> userValues) {
        YamlConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
        int preserved = 0;
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String key = entry.getKey();
            if (CONFIG_VERSION_KEY.equals(key)) {
                continue;
            }
            if (newConfig.contains(key)) {
                newConfig.set(key, entry.getValue());
                preserved++;
            }
        }
        try {
            newConfig.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("保存更新后的配置失败: " + e.getMessage());
        }
        plugin.getLogger().info("已保留 " + preserved + " 项用户自定义配置。");
    }

    /** 安全解析整数值，失败时返回默认值。 */
    private static int parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
