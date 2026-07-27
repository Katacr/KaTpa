package org.katacr.katpa.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;

import java.io.File;
import java.util.Map;

/** 从配置读取带占位符的消息，并转换为 Adventure 组件。 */
public final class MessageService {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private final KaTpaPlugin plugin;
    private YamlConfiguration language;

    /** 创建绑定插件配置的消息服务。 */
    public MessageService(KaTpaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** 从 lang 文件夹重新载入配置选定的语言文件。 */
    public void reload() {
        File defaultFile = new File(plugin.getDataFolder(), "lang/zh_CN.yml");
        if (!defaultFile.exists()) {
            plugin.saveResource("lang/zh_CN.yml", false);
        }
        String languageName = plugin.getConfig().getString("language", "zh_CN");
        File selectedFile = new File(plugin.getDataFolder(), "lang/" + languageName + ".yml");
        if (!selectedFile.isFile()) {
            plugin.getLogger().warning("找不到语言文件 " + selectedFile.getName() + "，使用 zh_CN.yml。");
            selectedFile = defaultFile;
        }
        language = YamlConfiguration.loadConfiguration(selectedFile);
    }

    /** 向接收者发送带统一前缀的配置消息。 */
    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        sender.sendMessage(component(key, replacements, true));
    }

    /** 向接收者发送不含占位符的带前缀消息。 */
    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    /** 向玩家 ActionBar 发送不带聊天前缀的配置消息。 */
    public void sendActionBar(Player player, String key, Map<String, String> replacements) {
        player.sendActionBar(component(key, replacements, false));
    }

    /** 向玩家 ActionBar 发送不含占位符的配置消息。 */
    public void sendActionBar(Player player, String key) {
        sendActionBar(player, key, Map.of());
    }

    /** 生成配置消息组件，可选择是否附加统一前缀。 */
    public Component component(String key, Map<String, String> replacements, boolean prefix) {
        String value = text(key, replacements);
        String prefixText = prefix ? language.getString("prefix", "") : "";
        return SERIALIZER.deserialize(prefixText + value);
    }

    /** 返回完成占位符替换后的原始语言文本。 */
    public String text(String key, Map<String, String> replacements) {
        String value = language.getString(key, "&c缺少语言节点: " + key);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            value = value.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return value;
    }

    /** 返回不含占位符的原始语言文本。 */
    public String text(String key) {
        return text(key, Map.of());
    }
}
