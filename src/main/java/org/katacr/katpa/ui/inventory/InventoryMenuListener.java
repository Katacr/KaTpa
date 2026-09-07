package org.katacr.katpa.ui.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.katacr.katpa.ui.inventory.gui.GuiManager;
import org.katacr.katpa.ui.inventory.gui.GuiMenuHolder;

/**
 * 全局库存点击与关闭监听，将事件路由到对应菜单实现。
 *
 * 识别两种库存持有者：
 * <ul>
 *   <li>{@link GuiMenuHolder} — 由 {@code gui/} YAML 驱动的菜单，路由到 {@link GuiManager}</li>
 *   <li>{@link InventoryMenu.MenuHolder} — 编程式构建的菜单（如占位菜单）</li>
 * </ul>
 */
public final class InventoryMenuListener implements Listener {
    private final GuiManager gui;

    /** 创建绑定 GUI 管理器的监听器。 */
    public InventoryMenuListener(GuiManager gui) {
        this.gui = gui;
    }

    /** 仅当点击落在 KaTpa 菜单库存内时分发到对应菜单。 */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof GuiMenuHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0) {
                String clickType = event.isRightClick() ? "right" : "left";
                gui.handleClick(player, event.getRawSlot(), clickType);
            }
            return;
        }
        InventoryMenu menu = InventoryMenu.menuOf(holder);
        if (menu != null) {
            menu.handleClick(event);
        }
    }

    /** 玩家主动关闭菜单时注销其活跃会话。 */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof GuiMenuHolder && event.getPlayer() instanceof Player player) {
            gui.handleClose(player);
            return;
        }
        InventoryMenu menu = InventoryMenu.menuOf(holder);
        if (menu != null && event.getPlayer() instanceof Player player) {
            InventoryMenuRegistry.unregister(player.getUniqueId());
        }
    }
}
