package org.katacr.katpa.ui.inventory;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护玩家唯一标识到当前容器菜单的映射，供 {@link InventoryMenuListener} 路由点击。
 */
public final class InventoryMenuRegistry {
    private static final Map<UUID, InventoryMenu> ACTIVE = new ConcurrentHashMap<>();

    private InventoryMenuRegistry() {
    }

    /** 注册某玩家当前打开的菜单。 */
    static void register(InventoryMenu menu) {
        ACTIVE.put(menu.ownerId(), menu);
    }

    /** 注销某玩家的菜单；仅当仍是同一实例时移除，避免误清后续打开的菜单。 */
    static void unregister(UUID ownerId) {
        ACTIVE.remove(ownerId);
    }

    /** 返回某玩家当前活跃的菜单，无则返回 null。 */
    static InventoryMenu current(UUID ownerId) {
        return ACTIVE.get(ownerId);
    }
}
