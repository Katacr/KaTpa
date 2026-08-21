package org.katacr.katpa.service;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.katacr.katpa.KaTpaPlugin;

import java.util.Locale;

/** 负责生成吟唱期间环绕旅行者的可配置末影风格粒子。 */
public final class ParticleService {
    private final KaTpaPlugin plugin;
    private boolean warnedInvalidParticle;

    /** 创建绑定插件配置的粒子服务。 */
    public ParticleService(KaTpaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 在旅行者身体周围生成一轮吟唱粒子，自动检查模块粒子开关。 */
    public void spawnWarmup(Player player, String module) {
        if (!plugin.getConfig().getBoolean("modules." + module + ".particles", true)) {
            return;
        }
        String path = "particles.warmup";
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) {
            return;
        }
        String particleName = plugin.getConfig().getString(path + ".particle", "PORTAL");
        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            int count = Math.max(0, plugin.getConfig().getInt(path + ".count", 12));
            double offsetX = Math.max(0.0, plugin.getConfig().getDouble(path + ".offset-x", 0.45));
            double offsetY = Math.max(0.0, plugin.getConfig().getDouble(path + ".offset-y", 0.9));
            double offsetZ = Math.max(0.0, plugin.getConfig().getDouble(path + ".offset-z", 0.45));
            double extra = Math.max(0.0, plugin.getConfig().getDouble(path + ".extra", 0.08));
            Location center = player.getLocation().add(0.0, 1.0, 0.0);
            player.getWorld().spawnParticle(particle, center, count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException exception) {
            if (!warnedInvalidParticle) {
                warnedInvalidParticle = true;
                plugin.getLogger().warning("忽略无效粒子配置 " + path + ": " + particleName);
            }
        }
    }
}
