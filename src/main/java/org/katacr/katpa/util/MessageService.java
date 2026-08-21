package org.katacr.katpa.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.katacr.katpa.KaTpaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 从配置读取带占位符的消息，并转换为 Adventure 组件。语言文件缺失键自动从 JAR 补全。 */
public final class MessageService {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private final KaTpaPlugin plugin;
    private YamlConfiguration language;
    private YamlConfiguration internalDefaults;

    /** 创建绑定插件配置的消息服务。 */
    public MessageService(KaTpaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** 从 lang 文件夹重新载入配置选定的语言文件，并加载 JAR 内置默认作为缺失键来源。 */
    public void reload() {
        saveDefaultLangFiles();
        String languageName = plugin.getConfig().getString("language", "zh_CN");
        File selectedFile = new File(plugin.getDataFolder(), "lang/" + languageName + ".yml");
        if (!selectedFile.isFile()) {
            plugin.getLogger().warning("找不到语言文件 " + selectedFile.getName() + "，使用 zh_CN.yml。");
            selectedFile = new File(plugin.getDataFolder(), "lang/zh_CN.yml");
        }
        language = YamlConfiguration.loadConfiguration(selectedFile);
        internalDefaults = loadInternalLang(languageName);
        if (internalDefaults == null) {
            internalDefaults = loadInternalLang("zh_CN");
        }
    }

    /** 向接收者发送带统一前缀的配置消息。 */
    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        sendComponent(sender, component(key, replacements, true));
    }

    /** 向接收者发送不含占位符的带前缀消息。 */
    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    /** 向玩家 ActionBar 发送不带聊天前缀的配置消息。 */
    public void sendActionBar(Player player, String key, Map<String, String> replacements) {
        sendActionBar(player, component(key, replacements, false));
    }

    /** 向玩家 ActionBar 发送不含占位符的配置消息。 */
    public void sendActionBar(Player player, String key) {
        sendActionBar(player, key, Map.of());
    }

    /** 以 Bukkit 通用的旧式文本 API 发送普通组件消息。 */
    public void sendComponent(CommandSender sender, Component message) {
        sender.sendMessage(SECTION_SERIALIZER.serialize(message));
    }

    /** 通过当前 Paper 或 Spigot 适配器发送组件 ActionBar。 */
    public void sendActionBar(Player player, Component message) {
        plugin.interactions().sendActionBar(player, message);
    }

    /** 生成配置消息组件，可选择是否附加统一前缀。 */
    public Component component(String key, Map<String, String> replacements, boolean prefix) {
        String value = text(key, replacements);
        String prefixText = prefix ? language.getString("prefix", "") : "";
        return SERIALIZER.deserialize(prefixText + value);
    }

    /** 返回完成占位符替换后的原始语言文本。 */
    public String text(String key, Map<String, String> replacements) {
        String value = resolveKey(key);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            value = value.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return value;
    }

    /** 返回不含占位符的原始语言文本。 */
    public String text(String key) {
        return text(key, Map.of());
    }

    /** 优先从用户语言文件读取，缺失时从 JAR 默认补全并写回磁盘。 */
    private String resolveKey(String key) {
        if (language.contains(key)) {
            return language.getString(key);
        }
        if (internalDefaults != null && internalDefaults.contains(key)) {
            String defaultValue = internalDefaults.getString(key);
            addMissingKey(key, defaultValue);
            return defaultValue;
        }
        return "&c缺少语言节点: " + key;
    }

    /** 将缺失的键值对追加到用户语言文件并保存。 */
    private void addMissingKey(String key, String value) {
        String languageName = plugin.getConfig().getString("language", "zh_CN");
        File file = new File(plugin.getDataFolder(), "lang/" + languageName + ".yml");
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
        if (disk.contains(key)) {
            language.set(key, disk.getString(key));
            return;
        }
        disk.set(key, value);
        try {
            disk.save(file);
            plugin.getLogger().info("语言文件已自动补全缺失键: " + key);
        } catch (Exception e) {
            plugin.getLogger().warning("补全语言文件缺失键失败: " + key + " - " + e.getMessage());
        }
        language.set(key, value);
    }

    /** 从 JAR 中加载指定语言的内置默认语言文件。 */
    private YamlConfiguration loadInternalLang(String languageName) {
        String path = "lang/" + languageName + ".yml";
        InputStream stream = plugin.getResource(path);
        if (stream == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("加载内置语言文件失败: " + path + " - " + e.getMessage());
            return null;
        }
    }

    /** 将 JAR 内置语言文件提取到磁盘（仅当文件不存在时）。 */
    private void saveDefaultLangFiles() {
        for (String lang : new String[]{"zh_CN", "en_US"}) {
            File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
            if (!file.exists()) {
                plugin.saveResource("lang/" + lang + ".yml", false);
            }
        }
    }
}
