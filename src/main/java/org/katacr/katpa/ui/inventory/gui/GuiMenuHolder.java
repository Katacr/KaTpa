package org.katacr.katpa.ui.inventory.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/**
 * 持有 GUI 菜单定义与变量，并绑定 Bukkit 库存。
 *
 * 由 {@link GuiManager} 在渲染时创建，供 {@code InventoryMenuListener} 识别本插件菜单并路由点击。
 */
public final class GuiMenuHolder implements InventoryHolder {
    private final GuiMenu menu;
    private final MenuSession session;
    private Inventory inventory;

    /** 创建绑定菜单定义与会话的持有者。 */
    public GuiMenuHolder(GuiMenu menu, MenuSession session) {
        this.menu = menu;
        this.session = session;
    }

    /** 返回绑定的菜单定义。 */
    public GuiMenu menu() {
        return menu;
    }

    /** 返回菜单会话（含参数与变量）。 */
    public MenuSession session() {
        return session;
    }

    /** 绑定实际库存对象。 */
    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
