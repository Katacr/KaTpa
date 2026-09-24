package org.katacr.katpa.ui.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 容器菜单基类，封装库存创建、打开、关闭与槽位点击分发。
 *
 * 每个菜单实例绑定一个 {@link MenuHolder}，由 {@link InventoryMenuListener} 在
 * 全局 {@link InventoryClickEvent} 中按 holder 路由到对应菜单。子类通过
 * {@link #button(int, ItemStack, Consumer)} 注册可点击按钮。
 */
public abstract class InventoryMenu implements InventoryHolder {
    protected final JavaPlugin plugin;
    private final UUID ownerId;
    private final MenuHolder holder;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    private Inventory inventory;

    /** 创建绑定玩家与插件实例的容器菜单。 */
    protected InventoryMenu(JavaPlugin plugin, Player owner) {
        this.plugin = plugin;
        this.ownerId = owner.getUniqueId();
        this.holder = new MenuHolder(this);
    }

    /** 返回该菜单的标题，子类按需覆盖。 */
    protected abstract String title();

    /** 返回该菜单的槽位数（箱子为 9 的倍数）。 */
    protected abstract int size();

    /** 渲染菜单内容；子类在此填充物品并调用 {@link #button} 注册点击。 */
    protected abstract void render();

    /** 构造库存并触发渲染，但不打开给玩家。 */
    public InventoryMenu build() {
        net.kyori.adventure.text.Component titleComponent = org.katacr.katpa.text.TextParser.parse(title());
        this.inventory = org.katacr.katpa.text.BukkitItemMetaCompat.createInventoryComponent(holder, size(), titleComponent);
        if (this.inventory == null) {
            this.inventory = Bukkit.createInventory(holder, size(), org.katacr.katpa.text.TextParser.toLegacy(titleComponent));
        }
        this.holder.bind(inventory);
        render();
        return this;
    }

    /** 构建并打开给绑定的玩家；若已打开其它菜单先关闭。 */
    public void open() {
        Player player = Bukkit.getPlayer(ownerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        InventoryMenuRegistry.register(this);
        build();
        player.openInventory(inventory);
    }

    /** 注册某个槽位的点击事件；点击时会取消默认行为并执行 consumer。 */
    protected void button(int slot, ItemStack item, Consumer<Player> action) {
        if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
        if (action != null) {
            actions.put(slot, action);
        }
    }

    /** 由监听器在玩家点击本菜单库存时调用，分发到注册的按钮动作。 */
    void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            event.setCancelled(true);
            action.accept((Player) event.getWhoClicked());
        } else if (event.getClickedInventory() == inventory) {
            event.setCancelled(true);
        }
    }

    /** 关闭并注销本菜单。 */
    public void close() {
        Player player = Bukkit.getPlayer(ownerId);
        if (player != null && player.getOpenInventory().getTopInventory().equals(inventory)) {
            player.closeInventory();
        }
        InventoryMenuRegistry.unregister(ownerId);
    }

    /** 返回绑定的玩家唯一标识。 */
    public UUID ownerId() {
        return ownerId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** 持有菜单引用并暴露给 Bukkit 的轻量库存持有者。 */
    static final class MenuHolder implements InventoryHolder {
        private final InventoryMenu menu;
        private Inventory inventory;

        MenuHolder(InventoryMenu menu) {
            this.menu = menu;
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        InventoryMenu menu() {
            return menu;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** 返回持有者对应的菜单，供监听器路由。 */
    static InventoryMenu menuOf(InventoryHolder holder) {
        return holder instanceof MenuHolder menuHolder ? menuHolder.menu() : null;
    }

    /** 返回某个玩家当前打开的菜单（若存在）。 */
    static InventoryMenu currentOf(Player player) {
        HumanEntity human = player;
        return menuOf(human.getOpenInventory().getTopInventory().getHolder());
    }
}
