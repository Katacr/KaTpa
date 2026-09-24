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
 * @param item      展示物品（已构建好，含头颅/名称/lore）；占位槽可为 null，由菜单的
 *                  {@code empty-display}/{@code lock-display} 决定展示
 * @param variables 点击该行时注入会话的变量（如 {@code members_name}）
 * @param kind      条目类型：{@link Kind#NORMAL} 使用按钮 {@code display}/{@code actions}；
 *                  {@link Kind#EMPTY} 使用 {@code empty-display}/{@code empty-actions}；
 *                  {@link Kind#LOCK} 使用 {@code lock-display}/{@code lock-actions}；
 *                  {@link Kind#LIT}/{@link Kind#UNLIT} 分别使用 {@code lit-display}/{@code unlit-display}
 *                  展示但沿用按钮自身的 {@code actions}
 */
record GuiListItem(ItemStack item, Map<String, String> variables, Kind kind) {
    /** 列表条目类型。 */
    enum Kind {
        /** 正常条目（已有数据）。 */
        NORMAL,
        /** 已解锁但未使用的空槽位。 */
        EMPTY,
        /** 未解锁（权限不足）的锁定槽位。 */
        LOCK,
        /** 选中/点亮状态（使用 {@code lit-display} 展示，动作同普通条目）。 */
        LIT,
        /** 未选中/未点亮状态（使用 {@code unlit-display} 展示，动作同普通条目）。 */
        UNLIT,
        /** 布局填充槽位：不渲染、点击不执行任何动作。 */
        BLANK
    }

    /** 以正常条目构建（兼容旧调用）。 */
    GuiListItem(ItemStack item, Map<String, String> variables) {
        this(item, variables, Kind.NORMAL);
    }
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
