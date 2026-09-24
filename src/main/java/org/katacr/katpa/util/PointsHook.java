package org.katacr.katpa.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * PlayerPoints 可选前置的反射封装。
 *
 * <p>PlayerPoints 为软依赖，未安装或 API 变更时全部方法安全降级：{@link #available()} 返回
 * false，余额查询返回 0，扣除/发放返回 false，不会抛异常。
 */
public final class PointsHook {
    private final Plugin plugin;
    private Object api;
    private Method lookMethod;
    private Method takeMethod;
    private Method giveMethod;

    /** 尝试挂载 PlayerPoints，未安装时返回一个不可用的实例。 */
    public PointsHook() {
        this.plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (plugin == null) {
            return;
        }
        try {
            Method getInstance = plugin.getClass().getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            Method getApi = plugin.getClass().getMethod("getAPI");
            this.api = getApi.invoke(instance);
            Class<?> apiClass = api.getClass();
            this.lookMethod = apiClass.getMethod("look", UUID.class);
            this.takeMethod = apiClass.getMethod("take", UUID.class, int.class);
            this.giveMethod = apiClass.getMethod("give", UUID.class, int.class);
        } catch (ReflectiveOperationException e) {
            this.api = null;
        }
    }

    /** 返回 PlayerPoints 是否可用。 */
    public boolean available() {
        return api != null;
    }

    /** 返回玩家当前点券余额，不可用时返回 0。 */
    public int balance(UUID playerId) {
        if (api == null) {
            return 0;
        }
        try {
            Object value = lookMethod.invoke(api, playerId);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /** 扣除玩家点券，成功返回 true；余额不足或不可用时返回 false。 */
    public boolean take(UUID playerId, int amount) {
        if (api == null || amount <= 0) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(takeMethod.invoke(api, playerId, amount));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** 发放点券给玩家，成功返回 true。 */
    public boolean give(UUID playerId, int amount) {
        if (api == null || amount <= 0) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(giveMethod.invoke(api, playerId, amount));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
