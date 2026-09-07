package org.katacr.katpa.ui.inventory;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 便捷构建库存展示物品，支持 {@code &} 颜色码、名称与多行说明。
 *
 * 仅用于容器菜单按钮，不引入额外依赖；颜色码在设置时统一转换。
 */
public final class ItemBuilder {
    private final Material material;
    private int amount = 1;
    private String name;
    private final List<String> lore = new ArrayList<>();

    /** 以指定材质开始构建物品。 */
    public ItemBuilder(Material material) {
        this.material = material;
    }

    /** 设置堆叠数量。 */
    public ItemBuilder amount(int amount) {
        this.amount = amount;
        return this;
    }

    /** 设置显示名称；支持 {@code &} 颜色码。 */
    public ItemBuilder name(String name) {
        this.name = colorize(name);
        return this;
    }

    /** 追加一行说明；支持 {@code &} 颜色码。 */
    public ItemBuilder lore(String line) {
        this.lore.add(colorize(line));
        return this;
    }

    /** 追加多行说明；支持 {@code &} 颜色码。 */
    public ItemBuilder lore(List<String> lines) {
        for (String line : lines) {
            lore(colorize(line));
        }
        return this;
    }

    /** 生成最终 {@link ItemStack} 并写入元数据。 */
    public ItemStack build() {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 将 {@code &} 颜色码转换为 Bukkit 内部节字符。 */
    private static String colorize(String input) {
        if (input == null) {
            return null;
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
