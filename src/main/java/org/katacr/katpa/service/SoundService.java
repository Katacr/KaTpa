package org.katacr.katpa.service;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 从配置读取并播放请求、倒计时和传送交互音效。 */
public final class SoundService {
    private final KaTpaPlugin plugin;
    private final Set<String> warnedPaths = new HashSet<>();

    /** 创建绑定插件配置的音效服务。 */
    public SoundService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 仅向指定玩家播放某个配置节点的音效。 */
    public void play(Player player, String soundId) {
        SoundSettings settings = settings(soundId);
        if (settings == null) {
            return;
        }
        player.playSound(player.getLocation(), settings.sound, SoundCategory.PLAYERS,
                settings.volume, settings.pitch);
    }

    /** 在指定位置播放某个配置节点的音效，让附近玩家都能听见。 */
    public void playAt(Location location, String soundId) {
        SoundSettings settings = settings(soundId);
        if (settings == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, settings.sound, SoundCategory.PLAYERS,
                settings.volume, settings.pitch);
    }

    /** 解析并校验单个音效配置，禁用或无效时返回 null。 */
    private SoundSettings settings(String soundId) {
        String path = "sounds." + soundId;
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) {
            return null;
        }
        String soundName = plugin.getConfig().getString(path + ".sound", defaultSound(soundId));
        try {
            String keyValue = soundName.toLowerCase(Locale.ROOT);
            NamespacedKey soundKey = NamespacedKey.fromString(
                    keyValue.contains(":") ? keyValue : "minecraft:" + keyValue);
            Sound sound = soundKey == null ? null : Registry.SOUNDS.get(soundKey);
            if (sound == null) {
                throw new IllegalArgumentException("Unknown sound: " + soundName);
            }
            float volume = (float) Math.max(0.0, plugin.getConfig().getDouble(path + ".volume", 1.0));
            float pitch = (float) Math.max(0.0, plugin.getConfig().getDouble(path + ".pitch", 1.0));
            return new SoundSettings(sound, volume, pitch);
        } catch (IllegalArgumentException exception) {
            if (warnedPaths.add(path)) {
                plugin.getLogger().warning("忽略无效音效配置 " + path + ": " + soundName);
            }
            return null;
        }
    }

    /** 返回内置音效节点的默认 Minecraft 注册表键。 */
    private String defaultSound(String soundId) {
        return switch (soundId) {
            case "request-received" -> "minecraft:block.note_block.bell";
            case "countdown" -> "minecraft:block.note_block.hat";
            case "teleport" -> "minecraft:entity.enderman.teleport";
            default -> "";
        };
    }

    /** 保存一次已校验的音效、音量和音调。 */
    private record SoundSettings(Sound sound, float volume, float pitch) {
    }
}
