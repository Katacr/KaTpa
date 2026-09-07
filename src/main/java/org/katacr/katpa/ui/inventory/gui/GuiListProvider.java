package org.katacr.katpa.ui.inventory.gui;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 列表型按钮的数据提供器。
 *
 * 菜单 YAML 中定义 {@code type: '<列表类型>'} 的按钮（如 {@code MEMBERS_LIST}/{@code WARP_LIST}）
 * 会由各类型对应的提供器填充实际条目。每条目返回展示物品与一组上下文变量，点击时变量注入
 * 会话并执行按钮自身的 actions（其中可引用 {@code {members_name}} 等条目变量）。
 */
public interface GuiListProvider {
    /**
     * 返回该类型对应的列表条目（已按页码截取）。
     *
     * @param player   打开菜单的玩家
     * @param type     列表类型标识（来自按钮 {@code type} 字段）
     * @param session  当前菜单会话（已预填菜单参数等变量）
     * @param page     当前页码（从 0 开始）
     * @param perPage  每页展示的槽位数（由菜单 layout 中该类型字符数量决定）
     * @return 列表条目集合；空集合表示无内容
     */
    List<GuiListItem> provide(PlayerLike player, String type, MenuSession session, int page, int perPage);
}

/**
 * 列表型按钮的单条数据。
 *
 * @param item      展示物品（已构建好，含头颅/名称/lore）
 * @param variables 点击该行时注入会话的变量（如 {@code members_name}）
 */
record GuiListItem(ItemStack item, Map<String, String> variables) {
}

/**
 * 最小化玩家抽象，避免 GUI 模块直接依赖 Bukkit Player 类型带来的编译耦合。
 */
interface PlayerLike {
    java.util.UUID getUniqueId();

    String getName();

    org.bukkit.entity.Player getBukkit();
}

/**
 * Bukkit Player 的 {@link PlayerLike} 适配器。
 */
final class BukkitPlayer implements PlayerLike {
    private final org.bukkit.entity.Player player;

    BukkitPlayer(org.bukkit.entity.Player player) {
        this.player = player;
    }

    @Override
    public java.util.UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public org.bukkit.entity.Player getBukkit() {
        return player;
    }
}
