package org.katacr.katpa.ui.inventory;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.katacr.katpa.KaTpaPlugin;
import org.katacr.katpa.model.RequestType;
import org.katacr.katpa.model.TeleportRequest;

import java.util.List;

/**
 * 单条传送请求的漏斗窗口（5 格横向一排）。
 *
 * 布局：[0]绿玻璃(接受) [1]绿玻璃(接受) [2]对方头颅 [3]红玻璃(拒绝) [4]红玻璃(拒绝)
 * 点击接受/拒绝后立即关闭窗口。
 */
public final class RequestHopperMenu extends InventoryMenu {

    /** 创建绑定插件、玩家与待处理请求的漏斗窗口。 */
    public RequestHopperMenu(JavaPlugin plugin, Player owner, TeleportRequest request, String senderName) {
        super(plugin, owner);
        this.request = request;
        this.senderName = senderName;
    }

    private final TeleportRequest request;
    private final String senderName;

    @Override
    protected String title() {
        String typeDisplay = request.type() == RequestType.TPA_HERE
                ? "邀请你前往" : "请求前往你";
        return ChatColor.DARK_GRAY + "KaTpa " + ChatColor.WHITE + typeDisplay;
    }

    @Override
    protected int size() {
        // Bukkit 要求库存大小为 9 的倍数，漏斗外形居中放在 9 格内
        return 9;
    }

    @Override
    protected void render() {
        ItemStack accept = simple(Material.GREEN_STAINED_GLASS_PANE, "&a&l接受",
                List.of("&7点击接受 &f" + senderName, "&7的传送请求"));
        ItemStack reject = simple(Material.RED_STAINED_GLASS_PANE, "&c&l拒绝",
                List.of("&7点击拒绝 &f" + senderName, "&7的传送请求"));
        ItemStack frame = simple(Material.GRAY_STAINED_GLASS_PANE, " ", null);

        // 漏斗外形：槽位 2,3 绿(接受) | 4 头颅 | 5,6 红(拒绝)，其余为边框
        button(0, frame, null);
        button(1, frame, null);
        button(2, accept, p -> accept(p));
        button(3, accept, p -> accept(p));
        button(4, skull(senderName), null);
        button(5, reject, p -> deny(p));
        button(6, reject, p -> deny(p));
        button(7, frame, null);
        button(8, frame, null);
    }

    /** 直接调用服务层接受请求并关闭窗口。 */
    private void accept(Player p) {
        KaTpaPlugin katpa = (KaTpaPlugin) plugin;
        katpa.requests().accept(p, request.id());
        close();
    }

    /** 直接调用服务层拒绝请求并关闭窗口。 */
    private void deny(Player p) {
        KaTpaPlugin katpa = (KaTpaPlugin) plugin;
        katpa.requests().deny(p, request.id());
        close();
    }

    /** 构建带名称与说明的简单物品。 */
    private ItemStack simple(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new java.util.ArrayList<>();
                for (String line : lore) {
                    colored.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 构建玩家头颅物品。 */
    private ItemStack skull(String ownerName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
            skull.setDisplayName(ChatColor.GREEN + ownerName);
            skull.setLore(List.of(
                    ChatColor.GRAY + "向你发送了传送请求",
                    ChatColor.GRAY + "左键接受 / 右键拒绝"));
            item.setItemMeta(skull);
        }
        return item;
    }
}
